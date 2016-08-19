# goatway

Simple gateway between jabber muc and telegram chat

## Installation (you need leiningen to compile code)

```
git clone https://github.com/hdghg/goatway.git
cd goatway
lein uberjar
```

## Usage

This project may be used as standalone program or as library of
another application. For example, standalone program cannot
send media from telegram to smack, but web-application can host
this content. We will review only standalone usage on this page

    GW_XMPP_ADDR=bot@jabber.ru
    GW_XMPP_PASSWD=tops3cr3t
    GW_TG_CHAT=1337
    GW_XMPP_ROOM=chat@conference.jabber.ru
    GW_TG_API=0123456789:S3cr3T

    $ java -jar target/goatway-0.5.0-standalone.jar

## Options

GW_TG_API:      Api key of telegram bot that will transfer messages
(Meet botfather to register one)

GW_XMPP_ADDR:   Xmpp address for goatway bot

GW_XMPP_PASSWD: Xmpp password for goatway bot

GW_XMPP_ROOM:   Xmpp multi-user chat. Bot will receive all messages from this room and
send it to GW_TG_CHAT and vise versa

GW_TG_CHAT:     (Non-mandatory) If not set, bot will enter discovery mode. It will help
you discover id of chat. If set properly, goatway will use it in same manner as
GW_XMPP_ROOM but vise versa

### Bugs

Forwarded messages is not always printed correctly

### Thanks
Thanks to users who inspired me to write this and found lots of bugs
@hedlx, @hpswgprk, @Arkar4, @dulo_t-34, @magic_cookie, @Iwakura

## License

Distributed under the Eclipse Public License either version 1.0 or any later version.