[![Release](https://jitpack.io/v/umjammer/vavi-net-ods.svg)](https://jitpack.io/#umjammer/vavi-net-ods)
[![Java CI](https://github.com/umjammer/vavi-net-ods/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-net-ods/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-net-ods/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/umjammer/vavi-net-ods/actions/workflows/codeql-analysis.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)

# vavi-net-ods

 * Optical Disc Sharing Server

## Install

### maven

 * https://jitpack.io/#umjammer/vavi-net-ods

### Mac

 * `brew install cdrtools`

## Usage

### setup

```shell
$ defaults write com.apple.NetworkBrowser EnableODiskBrowsing -bool true
$ killall Finder
```

### clean up

```sjell
$ pkill -f vavi.net.ods.OdsServer
$ defaults delete com.apple.NetworkBrowser EnableODiskBrowsing
```

## References

 * https://github.com/klattimer/pyods
 * https://github.com/nightwend/ODSServer
 * https://github.com/rcknr/ODSServer/blob/dev/ODSServer.js

## TODO

 * use aaru instead of Tools class
 * ~~😱 [noooooo](https://github.com/klattimer/pyods/issues/8)~~
