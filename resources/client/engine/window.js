// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'ace/editor', 'ace/undomanager', 'ace/virtual_renderer', 'engine/session', 'engine/commander', 'ace/commands/command_manager'],
function ($,        edit,         undomanager,       renderer,               session,          commander,          command_manager) {
  var instances = {},
      defaults = {
        loader: 'load-buffer',
        highline: true
      }, init;
  
  init = function ($el) {
    var el = $el.get(0);

    var doc = session.create({ contents: $el.text() });
    doc.setUndoManager(new undomanager.UndoManager());
    el.innerHTML = '';

    var editor = new edit.Editor(new renderer.VirtualRenderer(el, require("theme/engine")));
    editor.setSession(doc);

    var env = {};
    env.document = doc;
    env.editor = editor;
    editor.resize();
    $(window).resize(function () {
      editor.resize();
    });
    el.env = env;
    // Store env on editor such that it can be accessed later on from
    // the returned object.
    editor.env = env;
    return editor;
  };

  return {
    instances: instances,
    defaults: defaults,
    create: function (spec, my) {
      var that = init(spec.element), renderer = that.renderer, command;
      
      my = my ? my : {};
      spec = $.extend({}, defaults, spec);
      command = commander.create(my.commands);
      
      renderer.setShowGutter(false);
      renderer.setShowPrintMargin(false);
      renderer.setHScrollBarAlwaysVisible(false);

      that.io = spec.io;
      that.bufferName = spec.bufferName;
      that.setTheme(spec.theme);
      that.setFontSize(spec.fontSize);
      that.setHighlightActiveLine(spec.highline);
      that.setKeyboardHandler({ handleKeyboard: function (data, hashId, textOrKey, keyCode, e) {
        //console.log('got ' + hashId + ' [' + textOrKey + '] ' + keyCode);

        if (keyCode !== undefined) {
          that.io.emit('keyboard', hashId, textOrKey, keyCode, that.bufferName, that.responder());
        }

        return hashId === 0 || hashId === 4 ? {} : { command: 'noop' }; // noop prevents propagation of e.g. ctrl+r; empty object return still propagates event -> insertstring
      }});

      that.responder = function (callback) {
        return function (response) {
          if (Object.prototype.toString.call (response) !== '[object Array]') {
            response = [response];
          }

          response.forEach(function (value) {
            command.exec(value.command, { editor: that }, value.args);
          });

          if (callback) { callback(response); }
        };
      };

      that.clear = function () { // This is somehow missing in the ace implementation
        that.setSession(session.create({ contents: '' }));
      };

      // This is absolutely required to make insertion work, as-is.
      that.commands = new command_manager.CommandManager('win', [{
        name: 'insertstring', exec: function (editor, args) { // this must be *here*!
          editor.io.emit('keyboard', 0, args, undefined, editor.bufferName, that.responder());
        }
      }]);

      ['mousedown', 'dblclick', 'tripleclick', 'quadclick'].forEach (function (ev) {
        that.on(ev, function (e) {
          if (e.type == 'mousedown') {
            e.editor.io.emit('mouse', e.type, e.getButton(), e.getDocumentPosition(), that.bufferName, that.responder());
          }
          return e.preventDefault();
        });
      });

      that.io.emit(spec.loader, that.bufferName, function (contents, position) {
        that.setSession(session.create({ contents: contents }));
        that.moveCursorTo(position.row, position.column);
      });

      instances[spec.bufferName] = that;

      return that;
    }
  };
});
