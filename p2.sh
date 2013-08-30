#! /bin/sh

~/dev/eclipse/eclipse -application org.eclipse.equinox.p2.director \
  -repository http://download.eclipse.org/releases/kepler/ "$@"
