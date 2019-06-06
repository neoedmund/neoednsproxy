#!/bin/sh
# please run as ROOT
mydir=`dirname $0`
cd $mydir
java -cp dist/NeoeDnsProxy.jar neoe.dns.DnsProxy2 &
echo "nameserver 127.0.0.1" > /etc/resolv.conf
