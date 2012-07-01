// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery'], function ($) {
  return function (that) {
    that.on = function (event, callback) {
      $(that).on(event, callback);
    }

    // ace compat API
    that.addEventListener = that.on;
    
    return that;
  }
});
