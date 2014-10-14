neoednsproxy
============

Neoe Dns Proxy - A local DNS dispatcher


Problems it solves
--------------------------
1. DNS provided by ISP may be spam. So we need use **specified trusted DNS servers**
2. Using a *single* DNS will fail sometime, becomes slow sometime, So we need **a pool of servers**, and we get data from the fastest one.


Facts
---------------------------
*neoe* use this as the default DNS server on both Windows and Linux,
because especilly in countries like China, DNS servers are always being polutioned, spammed.


How about the performace? Will it slow down my internet browsing?
-------------------------
Probably not. DNS resolving is a small part of internet browsing. It usually cost more time downloading the web pages.

However neoednsproxy can **speed up** things because it uses a cache. If you want to refresh DNS records in cache, restart it,
or click "clear cache" if you could find the icon in tasktar.


Further topics
--------------------------
It just resolve DNS. No statistis. If you need you may hack the code.

Also it do not support black list domains, if you need it, you may look for an AdBlock plugin for firefox like [neoeblock](https://github.com/neoedmund/neoeblock), they can do more things than domain blocking.


