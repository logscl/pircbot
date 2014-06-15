pircbot
=======

Modified Pircbot (http://www.jibble.org/pircbot.php) to handle multiple incoming charsets.

By default, Pircbot cannot handle messages from different incoming charsets, resulting in badly decoded accents from messages in unicode/utf-8. This mod try do detect the encoding of every incoming message to standardize it to the JVM own charset.

The modifications are the following :

- Usage of Maven;
- Integration of the juniversalchardet library (https://code.google.com/p/juniversalchardet/) to detect encoding of the messages;
- Usage of the Log4j library and replaced every call to Pircbot's log method (and System.out.println).

