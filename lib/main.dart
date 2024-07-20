import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  static const platformNewPeer = MethodChannel('SendToAndroid');

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        appBar: AppBar(
          title: const Text("Flutter WebRTC"),
        ),
        body: MyHomePage(),
      ),
    );
  }
}

class MyHomePage extends StatefulWidget {
  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final TextEditingController _clientControllerNewPeer = TextEditingController();
  final TextEditingController _clientControllerTarget = TextEditingController();

  void SendToAndroid(String action) async {
    try {
      final Map<String, dynamic> params = {
        'peerName': _clientControllerNewPeer.text,
        'targetPeer': _clientControllerTarget.text,
        'action': action,
      };
      final String result = await MyApp.platformNewPeer.invokeMethod('handlePeerAction', params);
      print(result); // Handle the result from Android native code
    } on PlatformException catch (e) {
      print("Failed to send data: '${e.message}'.");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        children: [
          TextField(
            controller: _clientControllerNewPeer,
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              labelText: 'Add new Peer name, e.g., peer-A',
            ),
          ),
          const SizedBox(height: 10),
          ElevatedButton(
            onPressed: () => SendToAndroid('addPeer'),
            child: const Text('Add Peer'),
          ),
          const SizedBox(height: 30),

          TextField(
            controller: _clientControllerTarget,
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              labelText: 'Target peer name',
            ),
          ),
          const SizedBox(height: 10),
          ElevatedButton(
            onPressed: () => SendToAndroid('connect'),
            child: const Text('Connect'),
          ),
          const SizedBox(height: 10),
          ElevatedButton(
            onPressed: () => SendToAndroid('p2p-check'),
            child: const Text('P2P connection'),
          ),
        ],
      ),
    );
  }
}
