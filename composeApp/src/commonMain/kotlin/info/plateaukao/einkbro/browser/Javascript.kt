package info.plateaukao.einkbro.browser

/** JavaScript whitelist stub, seeded with sample entries for the catalog. */
class Javascript : BaseWebConfig(
    initialDomains = listOf(
        "github.com",
        "wikipedia.org",
    )
)
