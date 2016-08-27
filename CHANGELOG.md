# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## 0.5.5 - 2016-08-27
### Added
- Highlights are properly converted to telegram style

### Fixed
- Fixed some puppet nicknames being unignored after splitter reconnects

### Reworked
- Stanza id now generated manually to help identifying own messages
- During room connection when jid_malformed error thrown, reconnect room with nickname anonymous

## 0.5.2 - 2016-08-24
### Added
- Added logging to channels to help further debugging

## 0.5.1 - 2016-08-21
### Reworked
- Reworked filter fo filter by nick not jid

## 0.5.0 - 2016-08-20
### Added
- Created standalone project
