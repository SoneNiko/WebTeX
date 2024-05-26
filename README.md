example config:

```
{
    "serverConfig": {},
    "pathConfig": {},
    "defaultRouteSettings": {
        "generationConfig": {
            "type": "STATIC",
            "interval": 0
        }
    },
    "routesOverride": [
        {
            "generationConfig": { "type": "REBUILD_INTERVAL", "interval": 5 },
            "path": "/",
            "file": "test.tex"
        }
    ]
}
```
