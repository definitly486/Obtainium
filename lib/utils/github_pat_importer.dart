import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:pointycastle/export.dart';

const _encryptedPatUrl =
    'https://raw.githubusercontent.com/definitly486/Obtainium/main/api.github.obtainium.enc';
const _openSslSaltedPrefix = [0x53, 0x61, 0x6c, 0x74, 0x65, 0x64, 0x5f, 0x5f];

/// Downloads and decrypts an OpenSSL enc file produced with:
/// aes-256-cbc -pbkdf2 -iter 200000 (SHA-256).
Future<String> downloadAndDecryptGithubPat(String password) async {
  if (password.isEmpty) {
    throw const FormatException('Пароль не может быть пустым');
  }

  final response = await http.get(
    Uri.parse(_encryptedPatUrl),
    headers: const {'Accept': 'application/octet-stream'},
  );
  if (response.statusCode != 200) {
    throw Exception('Не удалось загрузить зашифрованный PAT (HTTP ${response.statusCode})');
  }

  final encrypted = response.bodyBytes;
  if (encrypted.length < 32 ||
      !_startsWith(encrypted, _openSslSaltedPrefix)) {
    throw const FormatException('Неверный формат зашифрованного файла');
  }

  final salt = Uint8List.fromList(encrypted.sublist(8, 16));
  final ciphertext = Uint8List.fromList(encrypted.sublist(16));
  if (ciphertext.isEmpty || ciphertext.length % 16 != 0) {
    throw const FormatException('Повреждённый зашифрованный файл');
  }

  final derivator = PBKDF2KeyDerivator(HMac(SHA256Digest(), 64));
  derivator.init(Pbkdf2Parameters(salt, 200000, 48));
  final keyAndIv = derivator.process(Uint8List.fromList(utf8.encode(password)));
  final key = Uint8List.fromList(keyAndIv.sublist(0, 32));
  final iv = Uint8List.fromList(keyAndIv.sublist(32, 48));

  final cipher = PaddedBlockCipherImpl(
    PKCS7Padding(),
    CBCBlockCipher(AESEngine()),
  );
  cipher.init(
    false,
    PaddedBlockCipherParameters<ParametersWithIV<KeyParameter>, Null>(
      ParametersWithIV<KeyParameter>(KeyParameter(key), iv),
      null,
    ),
  );

  final plaintext = cipher.process(ciphertext);
  final token = utf8.decode(plaintext, allowMalformed: false).trim();
  if (token.isEmpty || token.contains(RegExp(r'\\s'))) {
    throw const FormatException('Не удалось расшифровать GitHub PAT. Проверьте пароль.');
  }
  return token;
}

bool _startsWith(List<int> bytes, List<int> prefix) {
  if (bytes.length < prefix.length) return false;
  for (var i = 0; i < prefix.length; i++) {
    if (bytes[i] != prefix[i]) return false;
  }
  return true;
}
