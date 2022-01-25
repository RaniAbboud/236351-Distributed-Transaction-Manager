import json
import textwrap
import random

import Utils
import requests


def sendTransaction(servers, transaction):
    servers = list(servers)
    while len(servers):
        server = random.choice(servers)
        try:
            print("##" * 30)
            print(f"Sending regular Transaction using {server}")
            r = requests.post(
                f"http://{server}/transactions",
                json=[transaction],
                hooks={'response': Utils.print_roundtrip}
            ).json()
            print("##" * 30)
            return r
        except:
            print(f"Couldn't send to server {server}, removing it")
            servers.remove(server)
    assert 0, "No servers left!!"


def sendAtomicTransactionList(servers, transactionList):
    servers = list(servers)
    while len(servers):
        server = random.choice(servers)
        try:
            print("##" * 30)
            print(f"Sending Atomic Transaction List using {server}")
            r = requests.post(
                f"http://{server}/transactions",
                json=transactionList,
                hooks={'response': Utils.print_roundtrip}
            ).json()
            print("##" * 30)
            return r
        except:
            print(f"Couldn't send to server {server}, removing it")
            servers.remove(server)
    assert 0, "No servers left!!"

def sendCoins(servers, fromServer, toServer, coins):
    servers = list(servers)
    while len(servers):
        server = random.choice(servers)
        try:
            reqId = str(random.randint(1, 1 << 30))
            print("##" * 30)
            print(f"Sending {coins} coins from {fromServer} to {toServer} using {server} with reqId {reqId}")
            r = requests.post(
                f"http://{server}/send_coins",
                json={
                    "source_address": fromServer,
                    "target_address": toServer,
                    "coins": coins,
                    "request_id": reqId
                },
                hooks={'response': Utils.print_roundtrip}
            ).json()
            print("##" * 30)
            return r
        except:
            print(f"Couldn't send to server {server}, removing it")
            servers.remove(server)
    assert 0, "No servers left!!"


def listEntireHistory(servers, limit=None, suppress=False):
    servers = list(servers)
    while len(servers):
        server = random.choice(servers)
        try:
            server = random.choice(servers)
            params = dict()
            if (limit):
                params["limit"] = limit
            if not suppress:
                print("##" * 30)
                print(f"Sending List Entire History to {server} with limit {limit}")
            r = requests.get(
                f"http://{server}/transactions",
                params=params,
                hooks={'response': Utils.print_roundtrip if not suppress else None}
            ).json()
            if not suppress:
                print("##" * 30)
            return r
        except:
            if not suppress:
                print(f"Couldn't send to server {server}, removing it")
            servers.remove(server)
    assert 0, "No servers left!!"


def getAllTransactionsForUser(servers, address, limit=None):
    servers = list(servers)
    while len(servers):
        server = random.choice(servers)
        try:
            server = random.choice(servers)
            params = dict()
            if (limit):
                params["limit"] = limit
            print("##" * 30)
            print(f"Sending get history for user {address} to {server} with limit {limit}")
            r = requests.get(
                f"http://{server}/users/{address}/transactions",
                params=params,
                hooks={'response': Utils.print_roundtrip}
            ).json()
            print("##" * 30)
            return r
        except:
            print(f"Couldn't send to server {server}, removing it")
            servers.remove(server)
    assert 0, "No servers left!!"



def getAllUtxosForUser(servers, address):
    servers = list(servers)
    while len(servers):
        server = random.choice(servers)
        try:
            server = random.choice(servers)
            print("##" * 30)
            print(f"Sending get utxos for user {address} to {server}")
            r = requests.get(
                f"http://{server}/users/{address}/utxos",
                hooks={'response': Utils.print_roundtrip}
            ).json()
            print("##" * 30)
            return r
        except:
            print(f"Couldn't send to server {server}, removing it")
            servers.remove(server)
    assert 0, "No servers left!!"
