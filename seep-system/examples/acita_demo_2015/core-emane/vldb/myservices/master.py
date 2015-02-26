#
# CORE
# Copyright (c)2010-2012 the Boeing Company.
# See the LICENSE file included in this distribution.
#
''' Sample user-defined service.
'''

import os

from core.service import CoreService, addservice
from core.misc.ipaddr import IPv4Prefix, IPv6Prefix

class MeanderMaster(CoreService):
    ''' This is a sample user-defined service. 
    '''
    # a unique name is required, without spaces
    _name = "MeanderMaster"
    # you can create your own group here
    _group = "SEEP"
    # list of other services this service depends on
    _depends = ()
    # per-node directories
    _dirs = ()
    # generated files (without a full path this file goes in the node's dir,
    #  e.g. /tmp/pycore.12345/n1.conf/)
    _configs = ('master.sh', )
    # this controls the starting order vs other enabled services
    _startindex = 50
    #_starttime = "5"
    # list of startup commands, also may be generated during startup
    _startup = ('sh master.sh',)
    # list of shutdown commands
    _shutdown = ()


    @classmethod
    def generateconfig(cls, node, filename, services):
        ''' Return a string that will be written to filename, or sent to the
            GUI for user customization.
        '''
        repo_dir = "/data/dev/seep-github"
        seep_example_dir = "%s/seep-system/examples/acita_demo_2015"%repo_dir
        seep_jar = "seep-system-0.0.1-SNAPSHOT.jar"
        query_jar = "acita_demo_2015.jar"
        lib_dir = "lib"
        dist_dir = "dist"
        log_dir = "log"
        cfg = "#!/bin/sh\n"
        cfg += "# auto-generated by MeanderMaster (master.py)\n"
        cfg += "mkdir %s\n"%lib_dir
        cfg += "mkdir %s\n"%dist_dir
        cfg += "mkdir %s\n"%log_dir
        cfg += "cp %s/core-emane/vldb/config/run-master.py .\n"%(seep_example_dir)
        cfg += "cp %s/lib/%s %s\n"%(seep_example_dir, seep_jar, lib_dir)
        cfg += "cp %s/dist/%s %s\n"%(seep_example_dir, query_jar, dist_dir)
        cfg += 'echo "Starting MeanderMaster on `hostname` (`hostname -i`)."\n'
        cfg += 'echo "cating /etc/hosts"\n'
        cfg += 'cat /etc/hosts\n'
        cfg += "ip route > rt.log\n"
        cfg += "ifconfig > if.log\n"
        cfg += "./run-master.py\n"
        cfg += "touch master.shutdown"
        #cfg += "cat /tmp/master-stdin | java -jar %s/%s Master `pwd`/%s/%s Base &\n"%(lib_dir, seep_jar, dist_dir, query_jar)

        return cfg

# this line is required to add the above class to the list of available services
addservice(MeanderMaster)

