import argparse
import random
import sys
import time

''' Tests '''
import BasicTest
import RandomTest


def parseArguments():
    parser = argparse.ArgumentParser(description='Ledger Client')
    parser.add_argument('-s', '--servers', type=str,
                        help='Range of REST server ports in the format <StartPort>:<EndPort>')
    parser.add_argument('-t', '--test', type=str, help='The test to run')
    parser.add_argument('-r', '--random_seed', type=str, help='The random seed to be used, if applicable')
    return parser.parse_args()


def getServers(args):
    start, end = args.servers.split(":")
    return [f"localhost:{p}" for p in range(int(start), int(end) + 1)]


def getTest(args):
    return args.test


def getSeed(args):
    if (args.random_seed):
        print(f"Using given seed {args.random_seed}")
        return int(args.random_seed)
    else:
        seed = random.randrange(sys.maxsize)
        print(f"Using random seed {seed}")
        return seed


def main():
    ## Parse Arguments
    args = parseArguments()
    servers = getServers(args)
    test = getTest(args)
    seed = getSeed(args)
    random.seed(seed)
    ## Run test
    print(f"Running test {test} on seed {seed} with servers: {servers}")
    start_time = time.time()
    if (test == "BasicTest"):
        BasicTest.runTest(servers)
    elif (test == "RandomTest"):
        RandomTest.runTest(servers)
    else:
        print(f"Can't recognise test {test}")
        exit(-1)
    print("Test took %s seconds" % (time.time() - start_time))
    print("#### Done ####")


if __name__ == '__main__':
    main()
