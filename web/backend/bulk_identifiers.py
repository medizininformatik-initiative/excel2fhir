"""Disk-backed collision checks rebuilt from committed chunk registration journals."""
import json
import sqlite3

EXISTING = 'existing identifier'


class Registry:
    def __init__(self, path):
        # A previous process may have stopped after checking an unpublished chunk.
        # Rebuild from committed journals instead of trusting that tentative state.
        path.unlink(missing_ok=True)
        path.with_name(path.name + '-journal').unlink(missing_ok=True)
        self.path = path
        self.db = sqlite3.connect(path)
        self.db.execute('CREATE TABLE identifiers (system TEXT, value TEXT, owner TEXT, rule TEXT, '
                        'PRIMARY KEY (system, value, owner))')

    def close(self):
        self.db.close()
        self.path.unlink(missing_ok=True)

    def include(self, journal):
        with self.db, journal.open() as stream:
            for line in stream:
                item = json.loads(line)
                system, value, owner, rule = (item[key] for key in ('system', 'value', 'owner', 'rule'))
                conflict = self.db.execute(
                    'SELECT owner,rule FROM identifiers WHERE system=? AND value=? AND owner<>? '
                    'AND (rule<>? OR ?<>?) LIMIT 1',
                    (system, value, owner, EXISTING, rule, EXISTING)).fetchone()
                if conflict:
                    raise ValueError(f'Identifier collision for rule {rule} / {conflict[1]}: '
                                     f'{system} | {value} on {conflict[0]} and {owner}')
                self.db.execute('INSERT INTO identifiers VALUES (?,?,?,?) ON CONFLICT(system,value,owner) '
                                'DO UPDATE SET rule=CASE WHEN excluded.rule<>? THEN excluded.rule ELSE rule END',
                                (system, value, owner, rule, EXISTING))
