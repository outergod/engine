// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'ace/edit_session', 'ace/document', 'ace/selection', 'ace/mode/text', 'ace/lib/event_emitter', 'ace/lib/oop'],
function ($,        session,            document,       selection,       mode,            emitter,                 oop) {
  return {
    create: function (spec, my) {
      var that = Object.create(session.EditSession.prototype, {});
      oop.implement(that, emitter.EventEmitter);

      that.$modified = true;
      that.$breakpoints = [];
      that.$frontMarkers = {};
      that.$backMarkers = {};
      that.$markerId = 1;
      that.$rowCache = [];
      that.$wrapData = [];
      that.$foldData = [];
      that.$undoSelect = true;
      that.$foldData.toString = function() {
        var str = "";
        that.forEach(function(foldLine) {
          str += "\n" + foldLine.toString();
        });
        return str;
      }

      spec.io.emit(spec.loader, spec.bufferName, function (contents, position) {
        that.setDocument(new document.Document(contents));
        that.selection = new selection.Selection(that);
        that.selection.moveCursorTo(position.row, position.column);
        that.setMode(new mode.Mode());
        my.editor.setSession(that);
      });

      return that;
    }
  };
});
