// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'ace/lib/oop', 'ace/lib/event_emitter'], function ($, oop, event) {
  return function (that) {
    oop.implement(that, event.EventEmitter);
    that.trigger = that._emit;
    
    return that;
  }
});
