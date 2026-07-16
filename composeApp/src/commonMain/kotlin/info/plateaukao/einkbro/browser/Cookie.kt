package info.plateaukao.einkbro.browser

/** Cookie whitelist stub, seeded with sample entries for the catalog. */
class Cookie : BaseWebConfig(
    initialDomains = listOf(
        "login.example.com",
        "mail.google.com",
        "news.ycombinator.com",
    )
)
