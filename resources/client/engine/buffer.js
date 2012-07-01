// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'engine/eventuality'],
function ($,        eventuality) {
  return {
    create: function (spec, my) {
      var that = {};

      my = $.extend({}, { ready: function () {} }, my || {});

      eventuality(that);

      // TODO implement me
      that.isMultiLine = function () {
        return false;
      };

      // TODO implement me
      that.getCursor = function () {
        // see ace/anchor:$clipPositionToDocument
        return { row: 0, column: 0 };
      };

      // TODO implement me
      that.isEmpty = function () {
        // see ace/selection:isEmpty
        return true;
      };

      // TODO implement me
      that.getLength = function () {
        // see ace/document:getLength
        return 1; // number of lines
      };

      // TODO implement me
      that.getLine = function (row) {
        // see ace/document:getLine
        return "";
      };

      // TODO implement me
      that.getAllLines = function () {
        // see ace/document:getAllLines
        return [];
      };

      // TODO implement me
      that.getLines = function (firstRow, lastRow) {
        // see ace/document:getLines
        return [];
      };

      // TODO implement me
      that.moveCursorTo = function (row, column) {
        // see ace/selection:moveCursorTo
        return;
      };

      spec.io.emit(spec.loader, spec.bufferName, function (contents, position) {
        my.ready(that);
      });
      
      return that;
    }
  };
});
