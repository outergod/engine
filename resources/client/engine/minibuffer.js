// -*- mode: js; indent-tabs-mode: nil; -*-
define (['engine/window', 'jquery'],
function (window,          $) {
  return {
    create: function (spec) {
      var that = window.create($.extend(spec, { bufferName: '*minibuffer*' })),
          active = false, _focus = that.focus;

      that.focus = function () {
        if (active) {
          _focus();
        } else {
          $.pnotify({
            text: 'Minibuffer window is not active' ,
            type: 'info',
            icon: 'ui-icon ui-icon-info'
          });
        }
      };

      $(that.container).parent().height(that.renderer.lineHeight);

      return that;
    }
  };
});
