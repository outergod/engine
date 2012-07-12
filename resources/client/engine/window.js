// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'ace/editor', 'ace/virtual_renderer', 'engine/session', 'engine/commander'],
function ($,        edit,         render,                 session,          commander) {
  var instances = {},
      defaults = {
        loader: 'load-buffer',
        highline: true
      }, init;
  
  init = function (spec) {
    var el = spec.element.get(0), doc, renderer, editor, env;
    spec.element.empty();
    renderer = new render.VirtualRenderer(el, require(spec.theme))

    editor = new edit.Editor(renderer);
    editor.setHighlightSelectedWord(false); // maybe change back later

    session.create(spec, { editor: editor });

    renderer.setShowGutter(false);
    renderer.setShowPrintMargin(false);
    renderer.setHScrollBarAlwaysVisible(false);

    env = { 
      document: doc,
      editor: editor
    };

    editor.resize();
    $(window).resize(function () {
      editor.resize();
    });

    el.env = env;

    editor.env = env;
    editor.io = spec.io;
    editor.bufferName = spec.bufferName;
    editor.setFontSize(spec.fontSize);
    editor.setHighlightActiveLine(spec.highline)

    return editor;
  };

  return {
    instances: instances,
    defaults: defaults,
    create: function (spec, my) {
      var that, actions = {};
      
      my = my ? my : {};
      spec = $.extend({}, defaults, spec);
      that = init(spec);
      $.extend(actions, my.actions);
      
      that.responder = function () {
        var args = Array.prototype.slice.call(arguments), fn = args.shift();
        if (actions[fn] && typeof actions[fn] === 'function') {
          actions[fn].apply(that, args);
        }
      };

      // This is absolutely required to make insertion work, as-is.
      that.commands = commander.create({
        insertstring: function (editor, args) { // this must be *here*!
          editor.io.emit('keyboard', 0, args, undefined, editor.bufferName, that.responder);
        }
      });

      that.setKeyboardHandler({ handleKeyboard: function (data, hashId, textOrKey, keyCode, e) {
        //console.log('got ' + hashId + ' [' + textOrKey + '] ' + keyCode);

        if (keyCode !== undefined) {
          that.io.emit('keyboard', hashId, textOrKey, keyCode, that.bufferName, that.responder);
        }

        return hashId === 0 || hashId === 4 ? {} : { command: 'noop' }; // noop prevents propagation of e.g. ctrl+r; empty object return still propagates event -> insertstring
      }});

      ['mousedown', 'dblclick', 'tripleclick', 'quadclick'].forEach (function (ev) {
        that.on(ev, function (e) {
          if (e.type == 'mousedown') {
            e.editor.io.emit('mouse', e.type, e.getButton(), e.getDocumentPosition(), that.bufferName, that.responder);
          }
          return e.preventDefault();
        });
      });

      instances[spec.bufferName] = that;

      return that;
    }
  };
});
