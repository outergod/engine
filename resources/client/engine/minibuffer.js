// -*- mode: js; indent-tabs-mode: nil; -*-
define (['engine/window', 'jquery'],
function (window,          $) {
  var opts = { 
    bufferName: '*minibuffer*', 
    loader: 'load-minibuffer', 
    highline: false 
  }, actions = {
    'exit': function () {
      this.deactivate();
    }
  };

  return {
    create: function (spec) {
      var that = window.create($.extend({}, opts, spec), { actions: actions }),
          active = false, _focus = that.focus, target_editor;

      that.focus = function () {
        if (active) {
          _focus.apply(that);
        } else {
          $.pnotify({
            text: 'Minibuffer window is not active',
            type: 'info',
            icon: 'ui-icon ui-icon-info'
          });
        }
      };

      that.activate = function (target, args) {
        if (active) {
          that.focus();
        } else {
          that.io.emit('activate-minibuffer', that.bufferName, args, function () {
            active = true;
            target_editor = window.instances[target];
            that.focus();
          });
        }
      };

      that.deactivate = function () {
        active = false;
        if (target_editor) {
          target_editor.focus();
          target_editor = undefined;
        }
      };

      $(that.container).parent().height(that.renderer.lineHeight);
      return that;
    }
  };
});
