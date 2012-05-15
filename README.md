Engine - Engine new generation internet Emacs
=============================================
Engine is an attempt at creating an [Emacs class editor] using a very modern
[Lisp dialect], [Clojure], while exposing the user interface through latest
[web standards technologies] such as [HTML5] and [CSS3] with a [WebSockets]
and [socket.io] based communication layer in between.


Technological Background
------------------------
In contrast to traditional Emacsen such as [GNU Emacs] and [XEmacs], it uses
[ropes] instead of [gap buffers] to cope with random access insertion and
deletion speeds in large buffers, utilizes parallel computing where useful and 
runs buffer modifications in [Software Transactional Memory]. Thus, it can
properly support multithreaded access to buffers, a feature missed by many in
traditional Emacsen. 

The whole user interface, including output and input, is outsourced to the
[Ace code editor], with runtime modifications to redirect input into server-side
Engine. Further modification has been made to make Ace look more Emacsish as
well. To put it metaphorically, one can imagine that the otherwise standalone
Ace editor is turned into a brainless puppet through Engine.

Server side Engine is implemented on top of [Aleph], which is in turn based on
[Netty] and [Lamina], providing an asynchronous, channel/event-based
architecture.

Through its WebSockets/socket.io based interface, Engine could easily be
supplemented by a terminal emulator based text interface.

Even though it is partly web technology based, Engine is not meant to run in a
"cloud". Using new generation web standard, however, does away with the risks of
having to test and maintain compatibility with different graphical
toolkits. Furthermore, there's hardly any modern computer without a [JVM] and a
web browser, so Engine is likely to work even in untested environments.


The Beef inside Engine
----------------------
Whether or not Engine itself will turn into anything popular someday, the
socket.io and rope implementations are universally useful and can (and probably
will) become autonomous projects anytime.


Rationale
---------
Emacs class editors are the most powerful kind of editors available, at least in
potential. They bear the potential of any other kind of editor, including those
of the powerful [vi] class of editors. This is achieved by breaking with the
[Unix philosophy],  
_Write programs that do one thing and do it well. Write
programs to work together. Write programs to handle text streams, because that
is a universal interface._

Instead, Emacsen can rather be seen as input/output processing frameworks
running inside modern, virtual [Lisp machines]. From a certain perspective, the
end of physical Lisp machines has made Lisp a mocking guest thriving inside
modern, general-purpose computers, with Emacsen as one of its manifestations. A
Lisp machine, being a virtual operating system of its own, transforms the Unix
philosophy into
_Write functions that do one thing and do it well. Write functions to work
together. Write functions to handle lists, because that is a universal
interface._

Where Unix represents a perfect community, Lisp represents enlightenment.  
Emacs is the interface to God.


Further reading
---------------
   * [Ymacs]: A similar project, based upon Common Lisp and more traditional
     AJAX techniques
   * [Emacsen]: The emacswiki.org definition of Emacs-class editors is clearly a
     contradiction of the one above, nevertheless it should be taken into
     account


[Emacs class editor]: http://www.finseth.com/craft/
[Lisp dialect]: http://en.wikipedia.org/wiki/Lisp_(programming_language)
[Clojure]: http://clojure.org/
[web standards technologies]: http://www.w3.org/standards/
[HTML5]: http://en.wikipedia.org/wiki/HTML5
[CSS3]: http://www.w3.org/Style/CSS/current-work.en.html
[WebSockets]: http://en.wikipedia.org/wiki/WebSocket
[socket.io]: http://socket.io/

[GNU Emacs]: http://www.gnu.org/software/emacs/
[XEmacs]: http://www.xemacs.org/
[ropes]: http://en.wikipedia.org/wiki/Rope_(computer_science)
[gap buffers]: http://en.wikipedia.org/wiki/Gap_buffer
[Software Transactional Memory]: http://en.wikipedia.org/wiki/Software_transactional_memory
[Ace code editor]: http://ace.ajax.org/

[Aleph]: https://github.com/ztellman/alep
[Netty]: http://www.jboss.org/netty
[Lamina]: https://github.com/ztellman/lamina
[JVM]: http://en.wikipedia.org/wiki/Java_virtual_machine

[vi]: http://en.wikipedia.org/wiki/Vi
[Unix philosophy]: http://en.wikipedia.org/wiki/Unix_philosophy
[Lisp machines]: http://en.wikipedia.org/wiki/Lisp_machine

[Ymacs]: http://www.ymacs.org/
[Common Lisp]: http://en.wikipedia.org/wiki/Common_Lisp
[Emacsen]: http://emacswiki.org/emacs/Emacsen
