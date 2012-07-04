// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'engine/eventuality', 'ace/range'],
function ($,        eventuality,          range) {
  return {
    create: function (spec, my) {
      var that = {}, state = {}, update, load;

      my = $.extend({}, { ready: function () {} }, my || {});

      eventuality(that);

      update = function (lines, position) {
        state = {
          lines: lines,
          row: position.row,
          column: position.column
        };
      };

      load = function (name, lines, position) {
        if (name === spec.bufferName) {
          update (lines, position);
        }
      };

      that.__noSuchMethod__ = function (id, args) {
        alert('Unimplemented method '+id+' in buffer called');
      };

      // TODO implement me
      that.isMultiLine = function () {
        // used for highlighting stuff
        return false;
      };

      that.getCursor = function () {
        // see ace/anchor:$clipPositionToDocument
        // used for getPixelPosition (cursor), getCursorPosition (editor) -> highlight stuff, onCursorChange -> setSession
        return { row: state.row, column: state.column };
      };

      that.isEmpty = function () {
        // see ace/selection:isEmpty
        // used for onSelectionChange (editor)
        return state.lines.length === 0;
      };


      that.getLength = function () {
        // see ace/document:getLength
        // used by tokenizer, documentToScreenPosition, $clipPositionToDocument
        return state.lines.length;
      };

      that.getLine = function (row) {
        // see ace/document:getLine
        // used for documentToScreenPosition, $clipPositionToDocument or getLine (in session)
        return state.lines[row] || "";
      };

      that.getLines = function (firstRow, lastRow) {
        // see ace/document:getLines
        // used for tokenizer
        return state.lines.slice(firstRow, lastRow + 1);
      };

      that.getAllLines = function () {
        // see ace/document:getAllLines
        // used for getScreenWidth, $computeWidth
        return that.getLines(0, that.getLength());
      };

      // TODO implement me      
      that.getRange = function() {
        // see ace/selection:getRange
        // used to mark the selected text region, if applicable
        return new range.Range(0, 0, 0, 0);
      }

      // // TODO implement me
      // that.moveCursorTo = function (row, column) {
      //   // see ace/selection:moveCursorTo
      //   return;
      // };

      that.destroy = function () {
        spec.io.removeListener('buffer-update', load);
      };

      spec.io.emit(spec.loader, spec.bufferName, function (lines, position) {
        update(lines, position);
        my.ready(that);
      });
      
      spec.io.on('buffer-change', load);

      return that;
    }
  };
});
