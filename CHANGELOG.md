# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## 0.6.7 - 2016-09-12
### Fixed
- Fixed incorrect forward_from_chat action
- Correctly handle resource_constraint error.

## 0.6.3 - 2016-08-30
### Added
- Added more logging to xmpp channels


## 0.6.2 - 2016-08-28
### Added
- Command /who now gets vcard if has arguments

## 0.6.1 - 2016-08-28
### Added
- Added command /who to telegram side of bot.

## 0.6.0 - 2016-08-28
### Added
- Added database support

### Reworked
- Received stanzas now stored in database if db was configured
- Excluded compiled code from jar, run command is not little different

## 0.5.6 - 2016-08-27
### Fixed
- Fixed message forwarding

### Reworked
- In smack, telegram users will now be represented as they are in telegram: FirstName LastName

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
