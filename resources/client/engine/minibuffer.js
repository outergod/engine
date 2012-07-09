// -*- mode: js; indent-tabs-mode: nil; -*-
define (['engine/window', 'jquery'],
function (window,          $) {
  var opts = { 
    bufferName: '*minibuffer*', 
    loader: 'load-minibuffer', 
    highline: false 
  };

  var commands = {
    exit: function (env) {
      env.editor.deactivate();
    }
  };

  return {
    create: function (spec) {
      var that = window.create($.extend({}, opts, spec), { commands: commands }),
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

      that.activate = function (env, args) { 
        that.io.emit('activate-minibuffer', that.bufferName, args, that.responder(function () {
          active = true;
          target_editor = env.editor;
          that.focus();
        }));
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
