import random

import Requests
import Utils
from Utils import pformat, pprint, BASIC_CLIENTS_LIST


def runTest(servers):
    testName = "RandomTest"
    print(f"{testName}: Started")
    NUM_REQUESTS = 30
    AVAILABLE_REQUESTS = [
        "TRANSACTION",
        "COIN_TRANSFER",
        "ATOMIC_LIST",
        "LIST_ENTIRE_HISTORY",
        "LIST_HISTORY_FOR_USER",
        "LIST_UTXOS"
    ]

    firstTime = True
    THRESHOLD = 5
    cnt = 0
    for i in range(NUM_REQUESTS):
        chosenType = random.choice(AVAILABLE_REQUESTS)
        print(f"{testName}: Trying request of type {chosenType} - {i+1}/{NUM_REQUESTS}")

        cnt += 1
        ## Just get all history and available UTxOs at each transaction instead of carrying state by ourselves
        if (firstTime or ((cnt > THRESHOLD) and chosenType in ["TRANSACTION", "COIN_TRANSFER", "ATOMIC_LIST"])):
            history = Requests.listEntireHistory(servers, suppress=True)
            unused_utxos = Utils.getAllUnusedUTxOsFromHistory(history)
            cnt = 0
            ## firstTime = False

        if chosenType == "TRANSACTION":
            transaction = Utils.createRandomTransaction(unused_utxos)
            Requests.sendTransaction(servers, transaction)
        elif chosenType == "COIN_TRANSFER":
            fromServer, toServer, coins = Utils.createRandomCoinTransfer(unused_utxos)
            Requests.sendCoins(servers, fromServer, toServer, coins)
        elif chosenType == "ATOMIC_LIST":
            atomic_list = Utils.createRandomAtomicList(unused_utxos)
            Requests.sendAtomicTransactionList(servers, atomic_list)
        elif chosenType == "LIST_ENTIRE_HISTORY":
            if random.randint(1, 100) <= 20:
                history = Requests.listEntireHistory(servers)
                unused_utxos = Utils.getAllUnusedUTxOsFromHistory(history)
                cnt = 0
            else:
                Requests.listEntireHistory(servers, limit=random.randint(1, 100))
        elif chosenType == "LIST_HISTORY_FOR_USER":
            chosenUser = random.choice(Utils.BASIC_CLIENTS_LIST)
            if random.randint(1, 100) <= 20:
                Requests.getAllTransactionsForUser(servers, chosenUser)
            else:
                Requests.getAllTransactionsForUser(servers, chosenUser, limit=random.randint(1, 100))
        elif chosenType == "LIST_UTXOS":
            chosenUser = random.choice(Utils.BASIC_CLIENTS_LIST)
            Requests.getAllUtxosForUser(servers, chosenUser)
        else:
            assert 0, f"Unknown request type {chosenType}"

    print(f"{testName}: Finished")
