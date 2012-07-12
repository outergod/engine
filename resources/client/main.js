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
  }), modules;

  modules = [
    // oldschool/shim
    'ace/lib/fixoldbrowsers', 'jquery-ui', 'jquery.pnotify',
    // main
    'engine/window', 'engine/minibuffer', 'engine/socket.io', 'engine/commander',
    'engine/buffer', 'engine/eventuality',
    // supplementary
    'theme/engine'
  ];

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
  }) (modules, function () {
    loader.queue(5, 'Establishing WebSocket connection', function () { socket = io.connect(); });
    require(['engine/jquery', 'engine/window', 'engine/minibuffer', 'engine/socket.io'],
    function ($,               window,          minibuffer,          io) {
      loader.queue(65, 'Creating GUI', function () {
        var editor, minibuffer_editor;

        socket.on('error-message', function (message, title) {
          $.pnotify({
            title: title,
            type: 'error',
            icon: 'ui-icon ui-icon-script',
            text: message
          });
        });

        minibuffer_editor = minibuffer.create({
          element: $('#minibuffer'),
          io: socket,
          theme: 'theme/engine',
          fontSize: '13px'
        });

        socket.on('execute-extended-command', minibuffer_editor.activate);

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
