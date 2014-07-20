ANCSSample
==========

ANCS
known issue:
 1.Sometimes, onDescriptorWrite will receive status code is 133, I don't know how to deal with it.
   Now I don't reconnect the gatt server.
 2.When kill the application in process manager, the service will lost and reconnect,onStartCommand will
   be called, the startId is 3, How to fix it?

