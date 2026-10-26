package com.owlynights.scalablecapitalwidgets.api

/**
 * GraphQL documents taken from the Scalable CLI (src/graphql.rs, src/broker_shared.rs,
 * src/broker_queries.rs). Only read operations plus the login 2FA handshake are used.
 */
object Queries {

    const val IS_2FA_ON_LOGIN_ENABLED = """
query Is2faOnLoginEnabled(${'$'}input: Is2faOnLoginEnabledInput!) {
  is2faOnLoginEnabled(input: ${'$'}input) {
    enabled
    hasApprovedSession
  }
}
"""

    const val START_2FA_ON_LOGIN = """
mutation Start2faOnLogin(${'$'}input: Start2faOnLoginInput!) {
  start2faOnLogin(input: ${'$'}input) {
    mfaSessionId
  }
}
"""

    const val VALIDATE_2FA_ON_LOGIN = """
mutation Validate2faOnLogin(${'$'}input: Validate2faOnLoginInput!) {
  validate2faOnLogin(input: ${'$'}input) {
    status
  }
}
"""

    const val REVOKE_ACCESS_TOKEN = """
mutation revokeAuthAccessToken(${'$'}input: AuthAccessTokenInput!) {
  revokeAuthAccessToken(input: ${'$'}input)
}
"""

    const val WHOAMI = """
query WhoAmI(${'$'}id: ID!) {
  personOverview(id: ${'$'}id) {
    id
    externalId
    locale
    personalDetails {
      firstName
      lastName
    }
  }
}
"""

    const val RESOLVE_BROKER_IDS = """
query ResolveBrokerIds(${'$'}id: ID!) {
  account(id: ${'$'}id) {
    id
    brokerPortfolios {
      id
    }
  }
}
"""

    const val BROKER_OVERVIEW = """
query BrokerOverview(
  ${'$'}accountId: ID!
  ${'$'}portfolioId: ID!
  ${'$'}includeYearToDate: Boolean!
) {
  account(id: ${'$'}accountId) {
    brokerPortfolio(id: ${'$'}portfolioId) {
      valuation(includeYearToDate: ${'$'}includeYearToDate) {
        valuation
        securitiesValuation
        cryptoValuation
        timestampUtc {
          time
        }
        lastInventoryUpdateTimestampUtc {
          time
        }
        timeWeightedReturnByTimeframe {
          timeframe
          simpleAbsoluteReturn
        }
      }
    }
  }
}
"""

    const val BROKER_HOLDINGS = """
query BrokerHoldings(
  ${'$'}accountId: ID!
  ${'$'}portfolioId: ID!
  ${'$'}includeYearToDate: Boolean!
  ${'$'}quoteSource: MarketDataSource!
) {
  account(id: ${'$'}accountId) {
    brokerPortfolio(id: ${'$'}portfolioId) {
      inventory {
        items {
          isin
          name
          type
          inventory {
            position {
              filled
              pending
              blocked
              fifoPrice
            }
          }
          portfolioIsinPerformance {
            valuation
            currency
          }
          quoteTick(source: ${'$'}quoteSource, includeYearToDate: ${'$'}includeYearToDate) {
            midPrice
            currency
            timestampUtc {
              time
            }
            isOutdated
          }
        }
      }
    }
  }
}
"""

    const val BROKER_WATCHLIST = """
query BrokerWatchlist(
  ${'$'}accountId: ID!
  ${'$'}portfolioId: ID!
  ${'$'}includeYearToDate: Boolean!
  ${'$'}quoteSource: MarketDataSource!
) {
  account(id: ${'$'}accountId) {
    brokerPortfolio(id: ${'$'}portfolioId) {
      watchlist {
        items {
          isin
          name
          type
          quoteTick(source: ${'$'}quoteSource, includeYearToDate: ${'$'}includeYearToDate) {
            midPrice
            currency
            timestampUtc {
              time
            }
            isOutdated
          }
        }
      }
    }
  }
}
"""

    const val BROKER_CHART = """
query BrokerChart(${'$'}isin: String!, ${'$'}timeFrames: [TimeFrame!]!, ${'$'}includeYearToDate: Boolean!) {
  timeSeriesBySecurity(
    isin: ${'$'}isin
    timeFrames: ${'$'}timeFrames
    includeYearToDate: ${'$'}includeYearToDate
  ) {
    isin
    timeFrame
    currency
    source
    closingReferencePoint {
      midPrice
      timestampUtc {
        time
      }
    }
    dataPoints {
      midPrice
      timestampUtc {
        time
      }
    }
  }
}
"""

    const val BROKER_PORTFOLIO_CHART = """
query BrokerPortfolioChart(${'$'}accountId: ID!, ${'$'}portfolioId: ID!) {
  account(id: ${'$'}accountId) {
    id
    brokerPortfolio(id: ${'$'}portfolioId) {
      id
      timeseries(includeYearToDate: true) {
        timeframe
        closingReferencePoint {
          valuation
          absoluteReturn
          timestampUtc { time }
        }
        dataPoints {
          valuation
          absoluteReturn
          timestampUtc { time }
        }
      }
    }
  }
}
"""

    const val BROKER_SEARCH = """
query BrokerSecuritySearch(
  ${'$'}accountId: ID!
  ${'$'}portfolioId: ID!
  ${'$'}searchTerm: String!
  ${'$'}includeYearToDate: Boolean!
  ${'$'}quoteSource: MarketDataSource!
) {
  account(id: ${'$'}accountId) {
    brokerPortfolio(id: ${'$'}portfolioId) {
      simpleSecuritySearch(searchTerm: ${'$'}searchTerm) {
        items {
          isin
          name
          type
          quoteTick(source: ${'$'}quoteSource, includeYearToDate: ${'$'}includeYearToDate) {
            midPrice
            currency
            timestampUtc {
              time
            }
            isOutdated
          }
        }
      }
    }
  }
}
"""

    const val BROKER_QUOTE = """
query BrokerQuote(
  ${'$'}accountId: ID!
  ${'$'}portfolioId: ID!
  ${'$'}isin: ID!
  ${'$'}includeYearToDate: Boolean!
  ${'$'}quoteSource: MarketDataSource!
) {
  account(id: ${'$'}accountId) {
    brokerPortfolio(id: ${'$'}portfolioId) {
      security(isin: ${'$'}isin) {
        id
        isin
        name
        type
        quoteTick(source: ${'$'}quoteSource, includeYearToDate: ${'$'}includeYearToDate) {
          id
          isin
          midPrice
          currency
          bidPrice
          askPrice
          isOutdated
          timestampUtc {
            time
          }
        }
      }
    }
  }
}
"""
}
