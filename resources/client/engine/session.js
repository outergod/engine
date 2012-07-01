// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'ace/edit_session', 'engine/buffer',    'engine/eventuality', 'ace/mode/text'],
function ($,        session,            buffer,             eventuality,          mode) {
  var init = function () {
    var that = Object.create(session.EditSession.prototype);

    that.$modified = true;
    that.$breakpoints = [];
    that.$frontMarkers = {};
    that.$backMarkers = {};
    that.$markerId = 1;
    that.$rowCache = [];
    that.$wrapData = [];
    that.$foldData = [];
    that.$undoSelect = true;
    that.$foldData.toString = function () {
      var str = '';
      that.forEach(function (foldLine) {
        str += "\n" + foldLine.toString();
      });
      return str;
    };
    
    return that;
  };

  return {
    create: function (spec, my) {
      var that = init();

      buffer.create(spec, {
        ready: function (buffer) {
          that.setDocument(buffer);
          that.selection = buffer;
          that.setMode(new mode.Mode());
          my.editor.setSession(that);
        }
      });

      eventuality(that);

      return that;
    }
  };
});
