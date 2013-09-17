#! /bin/sh

pwd=`pwd`
~/dev/eclipse/eclipse -noSplash -debug -consoleLog\
  -application org.eclipse.equinox.p2.director \
  -repository http://download.eclipse.org/releases/kepler/ \
  -installIU org.eclipse.platform.ide,org.eclipse.wst.jsdt.feature.feature.group \
  -tag InitialState \
  -destination $pwd/reqage-ide/ -bundlepool $pwd/reqage-ide/ \
  -profile SDKProfile -roaming\
  -profileProperties org.eclipse.update.install.features=true \
  -p2.os $1 -p2.ws $2 -p2.arch $3
