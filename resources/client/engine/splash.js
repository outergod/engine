// -*- mode: js; indent-tabs-mode: nil; -*-
define(['engine/jquery'], function ($) {
  return {
    create: function (spec) {
      var that = {}, $el = $(spec.element), 
          $bar = $el.find('.splash-progressbar'), $status = $el.find('.splash-status'), fx;

      fx = spec.fx || {
        duration: 1000,
        easing: 'easeOutSine',
      };
      fx.queue = false;

      that.progress = (function (val) {
        return function (inc) {
          val += inc;
          if (val >= 100) { 
            fx.complete = function () {
              console.log('splash::done'); 
              that.vanish();
            };
          }
          $bar.children('.ui-progressbar-value').animate({ width: val + '%' }, fx);
        };
      })(0);

      that.status = function (status) {
          $status.text(status);
      };

      that.queue = function (inc, status, action) {
        that.status(status);
        action();
        that.progress(inc);
      };

      that.vanish = function (duration) {
        duration = duration || 500;
        $el.animate({ opacity: 0 }, {
          duration: duration,
          complete: function () { $el.remove(); }
        });
      }

      $bar.progressbar({ value: 0.1 });

      return that;
    }
  }
});
