/* build script used by [neoebuild](https://github.com/neoedmund/neoebuild) */
{ 
baseDir:".",
prjs:[
 [NeoeDnsProxy,., {main:"neoe.dns.DnsProxy2"}]
],
destDir:".",
debug:true,
source:7,
target:7,
}

