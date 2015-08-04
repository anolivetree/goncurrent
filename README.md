Goncurrent
============

Goncurrent provides the power of channel and select of Golang. Goncurrent works as you expect it to do.
See [sample](https://github.com/anolivetree/goncurrent/tree/master/goncurrent-sample/src/main/java) directory to see how to use it.

Channel
------

Channels are typed queue through which you can send and receive values. It's like a BlockingQueue in Java.

    Chan<Integer> ch1 = Chan.create(1) // '1' is the buffer length of the channel
    ch1.send(10); 
    Integer value = ch1.receive();
    
When the channel is full, send() blocks. When the channel is empty, receive() blocks.

### Unbuffered channel

Channels can be unbuffered by passing 0 to Chan.create(). 

    Chan<Integer> ch1 = Chan.create(0)
    
On unbuffered channel, send() and receive() blocks until other size is ready. You can use unbuffered channels for thread synchronization.

### Closing a channel

After you call close() to a channel, you don't receive any more values from the channel. receive() returns null after closing.

    Chan<Integer> ch1 = Chan.create(1)
    ch1.send(0);
    ch1.close();
    Integer value;
    value = ch1.receive(); // receives '0'
    value = ch1.receive(); // receives null

You can specify a terminator object when closing. receive() returns the terminator object after closing.

    Chan<Integer> ch1 = Chan.create(1)
    ch1.send(0);
    ch1.close(99); // '99' is a terminator object
    Integer value;
    value = ch1.receive(); // receives '0'
    value = ch1.receive(); // receives '99'

If you don't want to use terminator object, call receiveWithResult(). 

    Result<Integer> result = ch1.receiveWithResult();
    if (result.ok) {
        // use result.data
    }

Sending to a closed channel throws an Exception.

    Chan<Integer> ch1 = Chan.create(1)
    ch1.close();
    ch1.send(0); // throws an Exception.

### Iterator

You can iterate a channel until it's closed.

    for (Integer i : ch1) {
        //
    }

Select
--------

You can wait multiple channel operations using select. 

    Chan<Integer> ch1 = Chan.create(0);
    Chan<Integer> ch2 = Chan.create(0);
    Chan<Integer> ch3 = Chan.create(0);
    Chan<Integer> ch4 = Chan.create(0);

    Select select = new Select();
    while (true) {
        select.receive(ch1); // index=0
        select.receive(ch2); // index=1
        select.send(ch3, 10); // index=2
        select.send(ch4, 5); // index=3
        int index = select.select(); // wait until one of the channels is ready. 
        if (index == 0) {
            // data is read from ch1
            Integer value = (Integer)select.getData();
        }
    }

select() blocks until one of the channels is ready. Return value of select() is the index of the channel. If multiple channels are ready, it chooses one at random.

### Nonblocking select

select() blocks when no channel is ready. If you don't want to block, use selectNonblock() instead. It returns -1 when no channel is ready.

    Chan<Integer> ch1 = Chan.create(0);
    Select select = new Select();
    while (true) {
        select.send(ch1, 10);
        int index = select.selectNonblock(); // No channel is ready. Returns -1
        
Download
--------

[ ![Download](https://api.bintray.com/packages/anolivetree/maven/goncurrent/images/download.svg) ](https://bintray.com/anolivetree/maven/goncurrent/_latestVersion)

or Gradle(from jcenter)

    compile 'io.github.anolivetree:goncurrent:1.+'

License
-------

    Copyright (C) 2015 Hiroshi Sakurai

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

