import itertools
import json
import re
import textwrap
import random

import requests

BASIC_CLIENTS_LIST = [
    "GenesisAddress",
    "Sajy",
    "Rani",
    "Paxos",
    "Chord",
    "Tendermint",
    "Cassandra",
    "Redis"
]
MAX_ADDRESS_WIDTH = max(len(x) for x in BASIC_CLIENTS_LIST) + 2

def pformat(r):
    return json.dumps(r, indent=2, sort_keys=False)


def pprint(r):
    print(json.dumps(r, indent=2, sort_keys=False))

def replacement(match):
    return f'''{{ {match.group(1)}: {match.group(2)},{" "*(MAX_ADDRESS_WIDTH - len(match.group(2)))}  {match.group(3)}: {match.group(4)} }}'''

TWO_FIELD_DICT_PATTERN = '\{\s*(\S*)\s*\:\s*(\S*)\s*,\s*(\S*)\s*\:\s*(\S*)\s*\}'
def format_response_body(body):
    r = pformat(body)
    return re.sub(TWO_FIELD_DICT_PATTERN, replacement, r, flags=re.MULTILINE)

def print_roundtrip(response, *args, **kwargs):
    format_headers = lambda d: '\n'.join(f'{k}: {v}' for k, v in d.items())
    print(textwrap.dedent('''\
        ---------------- request ----------------
        {req.method} {req.url}
        {reqBody}
        ---------------- response ----------------
        {res.status_code} {res.reason} {res.url}
        {respBody}\
    ''').format(
        req=response.request,
        res=response,
        reqBody="" if not response.request.body else format_response_body(json.loads(response.request.body)),
        respBody=format_response_body(response.json()) if (response.status_code // 100 == 2) else pformat(
            response.json())
    ))


def getAllUnusedUTxOsFromHistory(history):
    r = [{"transaction_id": tx["transaction_id"], "address": output["address"], "coins": output["coins"]}
         for tx in history for output in tx["outputs"]
         if not any(output["address"] == input["address"] and tx["transaction_id"] == input["transaction_id"]
                    for tx2 in history for input in tx2["inputs"])]
    r = {k: list(g) for k, g in itertools.groupby(sorted(r, key=lambda x: x["address"]), lambda x: x["address"])}
    return r


def createRandomCoinTransfer(unused_utxos):
    source = random.choice(list(unused_utxos.keys()))
    target = random.choice(list(set(BASIC_CLIENTS_LIST) - {source}))
    coins = random.randint(1, sum(x["coins"] for x in unused_utxos[source]))
    return (source, target, coins)


def divideNumber(a, n):
    assert a >= n >= 1
    pieces = []
    for idx in range(n - 1):
        pieces.append(random.randint(1, a - sum(pieces) - n + idx + 1))
    pieces.append(a - sum(pieces))
    return pieces


def createRandomTransaction(unused_utxos):
    source = random.choice(list(unused_utxos.keys()))
    inputsWithCoins = random.sample(unused_utxos[source], random.randint(1, len(unused_utxos[source])))
    inputs = [{"address": input["address"], "transaction_id": input["transaction_id"]} for input in inputsWithCoins]
    totalCoins = sum(x["coins"] for x in inputsWithCoins)
    targets = random.sample(BASIC_CLIENTS_LIST, random.randint(1, min(len(BASIC_CLIENTS_LIST), totalCoins)))
    pieces = divideNumber(totalCoins, len(targets))
    random.shuffle(pieces)
    outputs = [{"address": t, "coins": pieces[i]} for i, t in enumerate(targets)]
    return {"inputs": inputs, "outputs": outputs}


def createRandomAtomicList(unused_utxos):
    numTransactions = random.randint(min(2, len(unused_utxos)), len(unused_utxos))
    atomicList = []
    for i in range(numTransactions):
        atomicList.append(createRandomTransaction(unused_utxos))
        sourceAddress = atomicList[-1]["inputs"][0]["address"]
        for input in atomicList[-1]["inputs"]:
            unused_utxos[sourceAddress] = [x for x in unused_utxos[sourceAddress] if
                                           x["transaction_id"] != input["transaction_id"]]
        if (len(unused_utxos[sourceAddress]) == 0):
            del unused_utxos[sourceAddress]
    return atomicList
