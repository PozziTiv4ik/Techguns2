"""Read the data-only Java serialization subset used by the original dungeon scans.

Never loads Java classes or executes readObject hooks. Unknown protocol features fail
closed instead of guessing at the scan. Handles retain identity (Structure has cycles).
"""
import struct


class JavaStream:
    def __init__(self, data):
        self.data, self.pos, self.handles = data, 0, []
        if self.take(4) != b'\xac\xed\x00\x05':
            raise ValueError('Not a Java serialization stream v5')

    def take(self, length):
        if length < 0 or self.pos + length > len(self.data):
            raise ValueError('Truncated Java stream')
        result = self.data[self.pos:self.pos + length]
        self.pos += length
        return result

    def number(self, kind):
        return struct.unpack('>' + kind, self.take(struct.calcsize('>' + kind)))[0]

    def utf(self):
        # These scans contain ASCII class/registry/enum names; reject other encodings.
        return self.take(self.number('H')).decode('ascii')

    def remember(self, value):
        self.handles.append(value)
        return value

    def annotations(self):
        result = []
        while self.data[self.pos] != 0x78:
            result.append(self.read())
        self.pos += 1
        return result

    def read(self):
        token = self.number('B')
        if token == 0x70:  # null
            return None
        if token == 0x71:
            return self.handles[self.number('I') - 0x7e0000]
        if token == 0x74:
            return self.remember(self.utf())
        if token == 0x72:  # class descriptor
            desc = self.remember({'name': self.utf(), 'uid': self.number('q')})
            desc['flags'] = self.number('B')
            desc['fields'] = []
            for _ in range(self.number('H')):
                kind, name = chr(self.number('B')), self.utf()
                signature = self.read() if kind in 'L[' else None
                desc['fields'].append((kind, name, signature))
            desc['annotations'] = self.annotations()
            desc['super'] = self.read()
            return desc
        if token == 0x73:  # object
            desc = self.read()
            obj = self.remember({'class': desc['name'], 'fields': {}, 'annotations': {}})
            ancestry = []
            while desc is not None:
                ancestry.append(desc)
                desc = desc['super']
            for desc in reversed(ancestry):
                if desc['flags'] not in (2, 3):
                    raise ValueError(f"Unsupported flags: {desc}")
                for kind, name, _ in desc['fields']:
                    obj['fields'][name] = self.read() if kind in 'L[' else self.number(
                        {'B': 'b', 'C': 'H', 'D': 'd', 'F': 'f', 'I': 'i', 'J': 'q', 'S': 'h', 'Z': '?'}[kind])
                if desc['flags'] & 1:
                    obj['annotations'][desc['name']] = self.annotations()
            return obj
        if token == 0x7e:  # enum
            desc = self.read()
            obj = self.remember({'class': desc['name']})
            obj['enum'] = self.read()
            return obj
        if token in (0x77, 0x7a):
            return self.take(self.number('B' if token == 0x77 else 'I'))
        raise ValueError(f'Unsupported Java stream token {token:#x} at {self.pos - 1}')


def read_java(data):
    stream = JavaStream(data)
    result = stream.read()
    if stream.pos != len(data):
        raise ValueError('Trailing Java stream data')
    return result


def java_list(obj):
    assert obj['class'] == 'java.util.ArrayList'
    entries = obj['annotations'][obj['class']]
    assert len(entries[0]) == 4
    assert struct.unpack('>i', entries[0])[0] == obj['fields']['size'] == len(entries) - 1
    return entries[1:]


def java_map(obj):
    assert obj['class'] == 'java.util.HashMap'
    entries = obj['annotations'][obj['class']]
    assert len(entries[0]) == 8
    assert struct.unpack('>ii', entries[0])[1] * 2 == len(entries) - 1
    return list(zip(entries[1::2], entries[2::2]))


def custom_utf(obj, class_name):
    value, = obj['annotations'][class_name]
    length, = struct.unpack('>H', value[:2])
    assert length == len(value) - 2
    return value[2:].decode('ascii')
