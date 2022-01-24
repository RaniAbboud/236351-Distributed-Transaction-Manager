
import Requests
import Utils
from Utils import pformat, pprint, BASIC_CLIENTS_LIST


def runTest(servers):
    testName = "BasicTest"
    print(f"{testName}: Started")

    Requests.sendCoins(servers, "GenesisAddress", "Sajy", 1)
    Requests.listEntireHistory(servers, 5)
    Requests.getAllTransactionsForUser(servers, "Sajy")
    Requests.getAllTransactionsForUser(servers, "Sajy", 3)
    Requests.getAllUtxosForUser(servers, "Sajy")
    history = Requests.listEntireHistory(servers)


    history = Requests.listEntireHistory(servers)
    unused_utxos = Utils.getAllUnusedUTxOsFromHistory(history)
    fromServer, toServer, coins = Utils.createRandomCoinTransfer(unused_utxos)
    Requests.sendCoins(servers, fromServer, toServer, coins)

    history = Requests.listEntireHistory(servers)
    unused_utxos = Utils.getAllUnusedUTxOsFromHistory(history)
    transaction = Utils.createRandomTransaction(unused_utxos)
    Requests.sendTransaction(servers, transaction)

    history = Requests.listEntireHistory(servers)
    unused_utxos = Utils.getAllUnusedUTxOsFromHistory(history)
    atomic_list = Utils.createRandomAtomicList(unused_utxos)
    Requests.sendAtomicTransactionList(servers, atomic_list)

    print(f"{testName}: Finished")
