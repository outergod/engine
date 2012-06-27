// -*- mode: js; indent-tabs-mode: nil; -*-
requirejs.config({
  shim: {
    'jquery-ui': ['jquery'],
    'jquery.pnotify': ['jquery-ui'],
    'engine/socket.io': ['socket.io']
  }
});

require(['engine/splash'], function (splash) {
  var socket, loader = splash.create({
    element: '#splash'
  });

  (function (modules, rest) {
    var load = function () {
      var args = Array.prototype.splice.call(arguments, 0), current;
      if (args.length) {
        current = args.shift();
        loader.queue(50/modules.length, 'Loading AMD module ' + current, function () {
          require([current], function () { load.apply(null, args); });
        });
      } else {
        rest();
      }
    };

    load.apply(null, modules);
  }) (['ace/ace', 'theme/engine', 'engine/window', 'engine/minibuffer', 'engine/socket.io', 'jquery-ui', 'jquery.pnotify'], function () {
    loader.queue(25, 'Establishing WebSocket connection', function () { socket = io.connect(); });
    require(['engine/jquery', 'engine/window', 'engine/minibuffer', 'engine/socket.io'],
    function ($,               window,          minibuffer,          io) {
      loader.queue(25, 'Creating GUI', function () {
        var editor;

        socket.on('broadcast', function (command) {
          var editor = window.instances[command.args.buffer];
          if (editor) {
            editor.commands.exec(command.command, { editor: editor }, command.args);
          }
        });

        editor = window.create({
          element: 'editor',
          io: socket,
          bufferName: '*scratch*',
          theme: 'theme/engine',
          fontSize: '13px'
        });

        minibuffer.create({
          element: 'minibuffer',
          io: socket,
          theme: 'theme/engine',
          fontSize: '13px'
        });

        editor.focus();

        $.extend($.pnotify.defaults, {
          styling: 'jqueryui',
          history: false,
          delay: 3000,
          animation: {
            effect_in: 'drop',
            effect_out: 'drop',
            options_in: { direction: 'up' },
            options_out: { direction: 'up' }
          }
        });
      });
    });
  });
});
