# play-blog

A blog engine built upon [Play Framework](https://www.playframework.com) with Markdown experience by leveraging [Pegdown](https://github.com/sirthias/pegdown/) and [github-markdown-css](https://github.com/sindresorhus/github-markdown-css). It is designed to be a simple **multi-user** blog backed by **SQLite**. See [this](http://www.rooftrellen.org/) sample blog.

## Features

- [x] Markdown
- [x] Multi-user
- [x] Restricted registration via admin
- [x] Tag

## Deployment on Mac/Linux

- Prerequisites
    - [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
    - [Lightbend Activator](http://www.lightbend.com/activator/download)

- Steps on Terminal
    1. Download the source and enter the root directory, which contains `build.sbt`.

    2. Run `activator`.

        ```
        $ activator
        ```

    3. Execute `dist` task to generate the executable play-blog.

        ```
        [play-blog] $ dist
        ```

    4. Exit `activator`.

        ```
        [play-blog] $ exit
        ```

    5. Unzip the generated zip file and enter the root directory, which contains `bin/`.

    6. Consider changing `changeme` placeholders inside page templates and also the favicon picture.

    7. Run the executable play-blog with all the configurations.

        ```
        $ bin/play-blog -Dhttp.port=80 -Dplay.evolutions.db.default.autoApply=true -Dplay.crypto.secret="[play-secret]" -Dadmin.password="[admin-password]"
        ```

    8. Have an icecream. 🍦
