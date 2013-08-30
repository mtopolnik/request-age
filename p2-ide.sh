#! /bin/sh

~/dev/eclipse/eclipse -application org.eclipse.equinox.p2.director \
  -repository http://download.eclipse.org/releases/kepler/ \
  -installIU org.eclipse.platform.ide,org.eclipse.wst.jsdt.feature.feature.group \
  -tag InitialState \
  -destination ./reqage-ide/ -bundlepool ./reqage-ide/ \
  -profile SDKProfile \
  -profileProperties org.eclipse.update.install.features=true \
  -p2.os macosx -p2.ws cocoa -p2.arch x86_64
