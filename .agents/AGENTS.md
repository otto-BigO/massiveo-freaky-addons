# Project-Scoped Rules for Massiveo's Freaky Addons

## GitHub Release Process

You are creating a GitHub release for a Minecraft Forge 1.8.9 mod called "Massiveo's Freaky Addons".

Repo: github.com/otto-BigO/massiveo-freaky-addons (public)
Local checkout: /Users/otto/Desktop/CelleScanner
The mod is built with Gradle (ForgeGradle) and REQUIRES Java 8.

### Prerequisites
- The `gh` CLI must be installed and authenticated (`gh auth status` to check).
- Java 8 at: /Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home

### Two kinds of release
- FINAL release (e.g. v1.1.6): published as "Latest", targets the `main` branch.
- TEST build (e.g. v1.1.6-t1): a pre-release for testing, targets `hud-editor` (the working branch). Day-to-day work + test builds live on `hud-editor`; `main` only ever holds finished, finalized releases.

### Steps for a FINAL release (do these in order)

1. Set the version. It must match in BOTH files:
   - src/main/java/com/otto/cellescanner/CelleScannerMod.java  -> `VERSION = "1.1.6";`
   - build.gradle  -> `version = "1.1.6"`

2. Build clean and verify. ALWAYS use `clean build`:
   `JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home" ./gradlew clean build`
   Then verify there are NO duplicate class copies (this repo is under OneDrive, which makes " 2.class" conflict copies that crash the game if packed):
   `unzip -l build/libs/cellescanner-1.1.6.jar | grep -c ' 2\.'`
   This MUST print 0. If it doesn't, or the build fails, fix it before continuing.

3. Update CHANGELOG.md: add a new `## 1.1.6` section at the top with the changes.

4. Commit on the `hud-editor` branch:
   `git add -A`
   `git commit -m "v1.1.6: <short summary of the changes>"`

5. Push and fast-forward main to it:
   `git push origin hud-editor`
   `git checkout main`
   `git merge --ff-only hud-editor`
   `git push origin main`
   `git checkout hud-editor` (switch back so future work stays on hud-editor)

6. Tag and push the tag:
   `git tag v1.1.6`
   `git push origin v1.1.6`

7. Copy the jar to the release name the auto-updater expects (it looks for MassiveoFreakyAddons-<version>.jar):
   `cp build/libs/cellescanner-1.1.6.jar /tmp/MassiveoFreakyAddons-1.1.6.jar`

8. Create the release (put the notes in a file to avoid shell-escaping issues):
   `gh release create v1.1.6 /tmp/MassiveoFreakyAddons-1.1.6.jar --latest --target main --title "v1.1.6" --notes-file /tmp/notes.md`

9. Verify:
   `gh release view v1.1.6 --json isPrerelease,tagName,assets -q '{tag:.tagName, prerelease:.isPrerelease, assets:[.assets[].name]}'`
   Expect prerelease=false and the asset MassiveoFreakyAddons-1.1.6.jar.

### For a TEST build instead of a final release
Same as above but: keep everything on `hud-editor` (skip the main merge in step 5), and in step 8 use `--prerelease --target hud-editor` instead of `--latest --target main`. Name the tag like `v1.1.6-t1`, and set the version to `1.1.6-t1` in step 1.

### Rules (strict)
- NEVER use em dashes or en dashes anywhere, in code, commit messages, changelog, or release notes. Use periods, commas, or parentheses.
- NEVER add a "Co-Authored-By" trailer to commits.
- Do NOT create new repos.
- If a `git` command ever hangs (OneDrive can stall the filesystem), kill any stuck git process (`pkill -9 git`), remove `.git/index.lock` if present, and retry.
