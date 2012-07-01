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
        loader.queue(30/modules.length, 'Loading AMD module ' + current, function () {
          require([current], function () { load.apply(null, args); });
        });
      } else {
        rest();
      }
    };

    load.apply(null, modules);
  }) (['ace/lib/fixoldbrowsers', 'engine/window', 'theme/engine', 'engine/minibuffer', 'engine/socket.io', 'engine/commander', 'jquery-ui', 'jquery.pnotify'], function () {
    loader.queue(5, 'Establishing WebSocket connection', function () { socket = io.connect(); });
    require(['engine/jquery', 'engine/window', 'engine/minibuffer', 'engine/socket.io', 'engine/commander'],
    function ($,               window,          minibuffer,          io,                 commander) {
      loader.queue(65, 'Creating GUI', function () {
        var editor, minibuffer_editor, command;

        command = commander.create();

        socket.on('broadcast', function (data) {
          var editor = window.instances[data.args.buffer];
          if (editor) {
            command.exec(data.command, { editor: editor }, data.args);
          }
        });

        minibuffer_editor = minibuffer.create({
          element: $('#minibuffer'),
          io: socket,
          theme: 'theme/engine',
          fontSize: '13px'
        });

        $.extend(commander.defaults.commands, { 'execute-extended-command': minibuffer_editor.activate });

        editor = window.create({
          element: $('#editor'),
          bufferName: '*scratch*',
          io: socket,
          theme: 'theme/engine',
          fontSize: '13px'
        });

        editor.focus();
      });
    });

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

    window.alert = function (message) {
      $.pnotify({
        title: 'Alert',
        type: 'error',
        icon: 'ui-icon ui-icon-script',
        text: message
      });
    };
  });
});
