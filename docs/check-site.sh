#!/usr/bin/env bash
# Assertions this project's documentation build must satisfy.
#
# Run by the shared site workflow in jlt-commons/ci-builds against the freshly
# built _site, with BASE_PATH exported.
#
# Run it locally the same way, from a docs-engine checkout:
#   bb build <path to this repo> && cd <this repo> \
#     && BASE_PATH=/raylib-ios bash docs/check-site.sh

set -euo pipefail
out=_site

test -f "$out/index.html"       || { echo "no homepage generated"; exit 1; }
test -f "$out/guide/index.html" || { echo "no guide page generated"; exit 1; }
test -f "$out/css/screen.css"   || { echo "static assets missing"; exit 1; }

# The scenes are the point of this site, and a missing asset dir is only a
# warning inside the engine, deliberately. It has to be an error here or the
# docs publish with every image broken.
imgs=$(find "$out/images" -name '*.gif' -o -name '*.png' 2>/dev/null | wc -l | tr -d ' ')
src_imgs=$(find docs/images -name '*.gif' -o -name '*.png' | wc -l | tr -d ' ')
test "$imgs" = "$src_imgs" \
  || { echo "copied $imgs images, expected $src_imgs"; exit 1; }

# Every internal URL on this site carries a prefix. One that did not render is
# a broken link that still looks like markup, so it will not show up as a 404
# in a log until someone clicks it.
! grep -rq '{{site-base}}' "$out"/index.html "$out"/guide/*.html \
  || { echo "unrendered template variable"; exit 1; }

# The homepage is bespoke rather than generated from a guide page, so assert it
# is actually the bespoke one. A missing :home-template silently falls back to
# the generic renderer, which looks fine and is the wrong page.
grep -q 'class="hero"' "$out/index.html" \
  || { echo "homepage is the generic fallback, not docs/templates/home.html"; exit 1; }

# Both guides must be there. Naming them means a rename cannot quietly drop one
# from the site while the build still passes.
for page in performance-on-a-phone porting-an-example; do
  test -f "$out/guide/$page.html" || { echo "missing guide page: $page"; exit 1; }
done

# The homepage links every scene, and the two most recent are the ones most
# likely to be forgotten when the grid is edited.
for img in lorenz.gif tesseract.gif; do
  grep -q "$img" "$out/index.html" || { echo "homepage does not show $img"; exit 1; }
  test -f "$out/images/$img"       || { echo "missing image: $img"; exit 1; }
done

# The base path has to reach the emitted HTML, not just site.edn. Getting this
# wrong is the failure mode the engine's own README calls out: the pages render
# perfectly and load the organization site's stylesheet.
grep -q "${BASE_PATH}/css/screen.css" "$out/index.html" \
  || { echo "base path ${BASE_PATH} is not in the emitted URLs"; exit 1; }

echo "check-site.sh: ok, $imgs images and $(find "$out/guide" -name '*.html' | wc -l | tr -d ' ') guide pages"
