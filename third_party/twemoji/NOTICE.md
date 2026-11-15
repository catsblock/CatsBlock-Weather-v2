# Twemoji

Source: https://github.com/jdecked/twemoji

Copyright 2020 Twitter, Inc and other contributors.
Graphics licensed under CC-BY 4.0: https://creativecommons.org/licenses/by/4.0/

## Bundled artwork

The six reaction images are Android vector drawables generated from the official Twemoji SVG artwork and stored in `app/src/main/res/drawable`:

| Drawable | Code point | Reaction |
| --- | --- | --- |
| `twemoji_1f929.xml` | U+1F929 | Star-struck |
| `twemoji_1f601.xml` | U+1F601 | Grinning |
| `twemoji_1f62d.xml` | U+1F62D | Crying |
| `twemoji_26a1.xml` | U+26A1 | Lightning |
| `twemoji_1f44d.xml` | U+1F44D | Thumbs up |
| `twemoji_1f44f.xml` | U+1F44F | Clapping |

## Modifications

The SVGs were converted to Android `VectorDrawable` XML without changing shapes, colors or proportions. Each drawable keeps the original 36 x 36 viewport. The single `<ellipse>` element in the crying face was expressed as an equivalent path so the artwork renders identically.

The artwork is bundled in the application, is drawn locally, and is never substituted by the platform emoji font.
