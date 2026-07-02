"""Read-only Robinhood MCP sync for tracker-pg Phase 1."""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

from mcp_client import RobinhoodMcpClient
from mcp_tool_utils import (
    build_sync_targets,
    extract_accounts,
    list_tool_names,
    parse_tool_payload,
    pick_probe_accounts,
)

LOGGER = logging.getLogger(__name__)

OPTION_POSITIONS_TOOL = "get_option_positions"
EQUITY_ORDERS_TOOL = "get_equity_orders"
EQUITY_QUOTES_TOOL = "get_equity_quotes"
QUOTE_BATCH_SIZE = 20
ORDERS_SYNC_LIMIT = 10


def _to_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _price_from_quote(quote: dict[str, Any]) -> float | None:
    return _to_float(
        quote.get("last_trade_price")
        or quote.get("price")
        or quote.get("mark_price")
        or quote.get("last_extended_hours_trade_price")
        or quote.get("previous_close")
    )


def _market_value_from_fields(
    *,
    quantity: Any,
    price: Any,
    multiplier: float = 1.0,
) -> float | None:
    qty = _to_float(quantity)
    px = _to_float(price)
    if qty is None or px is None:
        return None
    return round(qty * px * multiplier, 2)


def _market_value_from_row(row: dict[str, Any], *, option: bool = False) -> float | None:
    existing = _to_float(row.get("market_value") or row.get("equity") or row.get("value"))
    if existing is not None and existing != 0.0:
        if option:
            qty = _to_float(row.get("quantity") or row.get("contracts") or row.get("qty"))
            avg = _to_float(
                row.get("average_buy_price")
                or row.get("average_price")
                or row.get("average_open_price")
                or row.get("avg_cost")
            )
            if qty and avg:
                cost_style = abs(qty * avg)
                if cost_style > 0 and abs(existing / cost_style - 100.0) < 25.0:
                    return round(existing / 100.0, 2)
        return existing
    price = (
        row.get("current_price")
        or row.get("last_trade_price")
        or row.get("price")
        or row.get("mark_price")
        or row.get("last_extended_hours_trade_price")
    )
    multiplier = 100.0 if option else 1.0
    if option:
        mult = _to_float(row.get("trade_value_multiplier"))
        if mult is not None:
            multiplier = mult
        qty = _to_float(row.get("quantity") or row.get("contracts") or row.get("qty"))
        avg = _to_float(
            row.get("average_buy_price")
            or row.get("average_price")
            or row.get("average_open_price")
            or row.get("avg_cost")
        )
        if qty and avg and avg > 100:
            return round(abs(qty * avg), 2)
    return _market_value_from_fields(
        quantity=row.get("quantity") or row.get("contracts") or row.get("qty"),
        price=price,
        multiplier=multiplier,
    )


def _quotes_by_symbol(payload: Any) -> dict[str, float]:
    """Extract symbol → last price from get_equity_quotes payload."""
    prices: dict[str, float] = {}
    candidates: list[Any] = [payload]
    if isinstance(payload, dict):
        data = payload.get("data", payload)
        candidates = [data, payload]
        if isinstance(data, dict):
            for key in ("quotes", "results", "items"):
                items = data.get(key)
                if isinstance(items, list):
                    candidates = items
                    break

    quotes: list[dict[str, Any]] = []
    for candidate in candidates:
        if isinstance(candidate, list):
            quotes.extend(row for row in candidate if isinstance(row, dict))
        elif isinstance(candidate, dict):
            for key in ("quotes", "results", "items"):
                items = candidate.get(key)
                if isinstance(items, list):
                    quotes.extend(row for row in items if isinstance(row, dict))
            if candidate.get("symbol") or candidate.get("instrument_symbol"):
                quotes.append(candidate)

    for row in quotes:
        quote = row.get("quote") if isinstance(row.get("quote"), dict) else row
        symbol = quote.get("symbol") or quote.get("instrument_symbol")
        if isinstance(quote.get("instrument"), dict):
            symbol = symbol or quote["instrument"].get("symbol")
        price = _price_from_quote(quote)
        if symbol and price is not None:
            prices[str(symbol).strip().upper()] = price
    return prices


def _equity_cost_basis(row: dict[str, Any]) -> float | None:
    qty = _to_float(row.get("quantity") or row.get("shares") or row.get("qty"))
    avg = _to_float(
        row.get("average_buy_price")
        or row.get("average_price")
        or row.get("avg_cost")
    )
    if qty is None or avg is None:
        return None
    return round(abs(qty * avg), 2)


def _equity_needs_live_quote(position: dict[str, Any]) -> bool:
    """True when market_value is missing or equals cost (stale avg×qty placeholder)."""
    mv = _to_float(position.get("market_value") or position.get("equity") or position.get("value"))
    if mv in (None, 0.0):
        return True
    cost = _equity_cost_basis(position)
    if cost is None or cost == 0.0:
        return False
    return abs(mv - cost) < 0.05


def _enrich_market_values(
    client: RobinhoodMcpClient,
    positions: list[dict[str, Any]],
    tool_names: set[str],
) -> None:
    """Fill missing market_value using row prices, then get_equity_quotes."""
    for position in positions:
        existing = _to_float(position.get("market_value") or position.get("equity") or position.get("value"))
        if existing is not None and existing != 0.0:
            continue
        is_option = position.get("position_type") == "option"
        position["market_value"] = _market_value_from_row(position, option=is_option)

    if EQUITY_QUOTES_TOOL not in tool_names:
        return

    symbols_needing_quotes = sorted(
        {
            str(p["symbol"]).strip().upper()
            for p in positions
            if p.get("position_type") == "equity"
            and p.get("symbol")
            and _to_float(p.get("quantity")) is not None
            and _equity_needs_live_quote(p)
        }
    )
    if not symbols_needing_quotes:
        return

    quote_prices: dict[str, float] = {}
    for offset in range(0, len(symbols_needing_quotes), QUOTE_BATCH_SIZE):
        batch = symbols_needing_quotes[offset : offset + QUOTE_BATCH_SIZE]
        try:
            raw = client.call_tool(EQUITY_QUOTES_TOOL, {"symbols": batch})
            quote_prices.update(_quotes_by_symbol(parse_tool_payload(raw)))
        except Exception as exc:  # noqa: BLE001
            LOGGER.warning("get_equity_quotes failed for %s: %s", batch, exc)

    for (position in positions):
        if position.get("position_type") != "equity":
            continue
        if not _equity_needs_live_quote(position):
            continue
        symbol = str(position.get("symbol", "")).strip().upper()
        price = quote_prices.get(symbol)
        if price is None:
            continue
        position["market_value"] = _market_value_from_fields(
            quantity=position.get("quantity"),
            price=price,
        )


def _is_open_position(row: dict[str, Any]) -> bool:
    qty = _to_float(row.get("quantity"))
    return qty is not None and qty != 0.0


def _position_rank_value(row: dict[str, Any]) -> float:
    market_value = _to_float(row.get("market_value"))
    if market_value is not None:
        return abs(market_value)
    qty = _to_float(row.get("quantity"))
    price = _to_float(
        row.get("current_price") or row.get("average_buy_price") or row.get("average_price")
    )
    if qty is not None and price is not None:
        return abs(qty * price)
    return 0.0


def _trim_positions_for_sync(positions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Keep all open positions across accounts (equity + options)."""
    open_positions = [row for row in positions if _is_open_position(row)]
    open_positions.sort(key=_position_rank_value, reverse=True)
    return open_positions


def _orders_from_payload(payload: Any) -> list[dict[str, Any]]:
    """Best-effort extract order rows from get_equity_orders payload."""
    if not isinstance(payload, dict):
        return []
    data = payload.get("data", payload)
    rows: list[dict[str, Any]] = []
    if isinstance(data, dict):
        for key in ("orders", "equity_orders", "results", "items"):
            items = data.get(key)
            if isinstance(items, list):
                rows = [row for row in items if isinstance(row, dict)]
                break
    elif isinstance(data, list):
        rows = [row for row in data if isinstance(row, dict)]

    normalized = [_normalize_order(row) for row in rows]
    normalized = [row for row in normalized if row.get("robinhood_order_id")]
    seen_ids: set[str] = set()
    deduped: list[dict[str, Any]] = []
    for row in normalized:
        order_id = str(row["robinhood_order_id"])
        if order_id in seen_ids:
            continue
        seen_ids.add(order_id)
        deduped.append(row)
    deduped.sort(key=lambda row: str(row.get("updated_at") or row.get("created_at") or ""), reverse=True)
    return deduped[:ORDERS_SYNC_LIMIT]


def _normalize_order(row: dict[str, Any]) -> dict[str, Any]:
    instrument = row.get("instrument")
    symbol = row.get("symbol") or row.get("instrument_symbol")
    if not symbol and isinstance(instrument, dict):
        symbol = instrument.get("symbol")
    order_id = row.get("id") or row.get("order_id")
    return {
        "robinhood_order_id": str(order_id) if order_id else None,
        "symbol": str(symbol).strip().upper() if symbol else "",
        "side": row.get("side"),
        "order_type": row.get("type") or row.get("order_type"),
        "quantity": row.get("quantity") or row.get("cumulative_quantity"),
        "limit_price": row.get("price") or row.get("limit_price"),
        "average_price": row.get("average_price"),
        "state": row.get("state") or row.get("status"),
        "created_at": row.get("created_at"),
        "updated_at": row.get("updated_at") or row.get("last_transaction_at") or row.get("created_at"),
    }


def _positions_from_payload(payload: Any) -> list[dict[str, Any]]:
    """Best-effort extract position rows from get_equity_positions payload."""
    if not isinstance(payload, dict):
        return []
    data = payload.get("data", payload)
    if isinstance(data, dict):
        for key in ("positions", "equity_positions", "results", "items"):
            items = data.get(key)
            if isinstance(items, list):
                return [_normalize_position(row) for row in items if isinstance(row, dict)]
    if isinstance(data, list):
        return [_normalize_position(row) for row in data if isinstance(row, dict)]
    return []


def _normalize_position(row: dict[str, Any]) -> dict[str, Any]:
    instrument = row.get("instrument")
    symbol = row.get("symbol") or row.get("instrument_symbol")
    if not symbol and isinstance(instrument, dict):
        symbol = instrument.get("symbol")
    symbol_text = str(symbol).strip().upper() if symbol else ""
    normalized = {
        "position_type": "equity",
        "position_key": symbol_text,
        "symbol": symbol_text or symbol,
        "quantity": row.get("quantity") or row.get("shares") or row.get("qty"),
        "average_buy_price": row.get("average_buy_price")
        or row.get("average_price")
        or row.get("avg_cost"),
        "current_price": row.get("current_price")
        or row.get("last_trade_price")
        or row.get("price")
        or row.get("mark_price"),
        "market_value": row.get("market_value") or row.get("equity") or row.get("value"),
    }
    computed = _market_value_from_row({**row, **normalized}, option=False)
    if computed is not None:
        normalized["market_value"] = computed
    return normalized


def _positions_from_option_payload(payload: Any) -> list[dict[str, Any]]:
    """Best-effort extract option rows from get_option_positions payload."""
    if not isinstance(payload, dict):
        return []
    data = payload.get("data", payload)
    if isinstance(data, dict):
        for key in ("option_positions", "positions", "results", "items", "open_option_positions"):
            items = data.get(key)
            if isinstance(items, list):
                return [_normalize_option_position(row) for row in items if isinstance(row, dict)]
    if isinstance(data, list):
        return [_normalize_option_position(row) for row in data if isinstance(row, dict)]
    return []


def _looks_like_option_row(row: dict[str, Any]) -> bool:
    chain = row.get("chain_symbol") or row.get("underlying_symbol") or row.get("symbol")
    option_type = row.get("type") or row.get("option_type") or row.get("kind")
    strike = row.get("strike_price") or row.get("strike")
    expiration = row.get("expiration_date") or row.get("expiration") or row.get("expires_at")
    quantity = row.get("quantity") or row.get("contracts") or row.get("qty")
    return bool(chain and option_type and strike and expiration and quantity)


def _option_positions_from_portfolio(portfolio: Any) -> list[dict[str, Any]]:
    """Fallback: walk get_portfolio payload for embedded option position rows."""
    found: list[dict[str, Any]] = []
    seen_keys: set[str] = set()

    def visit(node: Any) -> None:
        if isinstance(node, dict):
            if _looks_like_option_row(node):
                normalized = _normalize_option_position(node)
                key = normalized["position_key"]
                if key not in seen_keys:
                    seen_keys.add(key)
                    found.append(normalized)
            for value in node.values():
                visit(value)
        elif isinstance(node, list):
            for item in node:
                visit(item)

    visit(portfolio)
    return found


def _normalize_option_position(row: dict[str, Any]) -> dict[str, Any]:
    chain = (
        row.get("chain_symbol")
        or row.get("underlying_symbol")
        or row.get("symbol")
        or row.get("underlying")
    )
    option_type = row.get("type") or row.get("option_type") or row.get("kind")
    strike = row.get("strike_price") or row.get("strike")
    expiration = row.get("expiration_date") or row.get("expiration") or row.get("expires_at")
    position_key = row.get("id") or row.get("option_id") or row.get("instrument_id") or row.get("position_id")
    if not position_key:
        position_key = f"{chain}|{option_type}|{strike}|{expiration}"
    elif option_type:
        # Robinhood may return separate long/short legs with the same option_id; keys must be unique per account.
        position_key = f"{position_key}|{str(option_type).lower()}"
    chain_text = str(chain).strip().upper() if chain else ""
    normalized = {
        "position_type": "option",
        "position_key": str(position_key),
        "symbol": chain_text or chain,
        "chain_symbol": chain_text or chain,
        "option_type": str(option_type).lower() if option_type else None,
        "strike_price": strike,
        "expiration_date": expiration,
        "quantity": row.get("quantity") or row.get("contracts") or row.get("qty"),
        "average_buy_price": row.get("average_price")
        or row.get("average_open_price")
        or row.get("average_buy_price")
        or row.get("avg_cost"),
        "current_price": row.get("current_price")
        or row.get("last_trade_price")
        or row.get("price")
        or row.get("mark_price"),
        "market_value": row.get("market_value") or row.get("value") or row.get("equity"),
    }
    computed = _market_value_from_row({**row, **normalized}, option=True)
    if computed is not None:
        normalized["market_value"] = computed
    return normalized


def _sync_option_positions(
    client: RobinhoodMcpClient,
    *,
    acct_num: str,
    role: str,
    portfolio_data: Any,
    option_tool_available: bool,
    warnings: list[str],
) -> list[dict[str, Any]]:
    options: list[dict[str, Any]] = []
    if option_tool_available:
        try:
            options_raw = client.call_tool(OPTION_POSITIONS_TOOL, {"account_number": acct_num})
            options_data = parse_tool_payload(options_raw)
            options = _positions_from_option_payload(options_data)
        except Exception as exc:  # noqa: BLE001
            LOGGER.warning("get_option_positions failed for •••%s: %s", acct_num[-4:], exc)
            warnings.append(
                f"Option sync failed for •••{acct_num[-4:]} ({role}): {exc}"
            )

    if not options:
        fallback = _option_positions_from_portfolio(portfolio_data)
        if fallback:
            options = fallback
            LOGGER.info(
                "Recovered %d option row(s) from get_portfolio for •••%s",
                len(fallback),
                acct_num[-4:],
            )

    for p in options:
        p["account_number"] = acct_num
        p["account_role"] = role
    return options


def run_sync(
    access_token: str,
    *,
    sync_default: bool = True,
    sync_all: bool = True,
) -> dict[str, Any]:
    client = RobinhoodMcpClient(access_token=access_token)
    started = datetime.now(timezone.utc).isoformat()
    warnings: list[str] = []
    try:
        client.initialize()
        tools = client.list_tools()
        tool_names = list_tool_names(tools)
        option_tool_available = OPTION_POSITIONS_TOOL in tool_names
        orders_tool_available = EQUITY_ORDERS_TOOL in tool_names
        if not option_tool_available:
            warnings.append(
                f"Option positions not synced: MCP exposes {len(tool_names)} tools and "
                f"does not include {OPTION_POSITIONS_TOOL!r} yet (Robinhood options read rollout). "
                "Re-run phase0_inventory.py after Robinhood enables option read tools."
            )

        raw_accounts = client.call_tool("get_accounts", {})
        accounts = extract_accounts(raw_accounts)
        picks = pick_probe_accounts(accounts)
        agentic = picks.get("agentic")

        if sync_all:
            targets = build_sync_targets(accounts)
        else:
            default = picks.get("default") if sync_default else None
            targets = []
            if agentic:
                targets.append(("agentic", agentic))
            if default and (not agentic or default.get("account_number") != agentic.get("account_number")):
                targets.append(("default", default))

        if not targets:
            raise RuntimeError("No accounts found from get_accounts")

        account_summaries: list[dict[str, Any]] = []
        all_positions: list[dict[str, Any]] = []
        all_orders: list[dict[str, Any]] = []
        portfolios: dict[str, Any] = {}

        for role, account in targets:
            acct_num = str(account["account_number"])
            portfolio_raw = client.call_tool("get_portfolio", {"account_number": acct_num})
            positions_raw = client.call_tool("get_equity_positions", {"account_number": acct_num})
            portfolio_data = parse_tool_payload(portfolio_raw)
            positions_data = parse_tool_payload(positions_raw)
            positions = _positions_from_payload(positions_data)
            for p in positions:
                p["account_number"] = acct_num
                p["account_role"] = role
            all_positions.extend(positions)

            options = _sync_option_positions(
                client,
                acct_num=acct_num,
                role=role,
                portfolio_data=portfolio_data,
                option_tool_available=option_tool_available,
                warnings=warnings,
            )
            all_positions.extend(options)

            account_orders: list[dict[str, Any]] = []
            if orders_tool_available:
                try:
                    orders_raw = client.call_tool(
                        EQUITY_ORDERS_TOOL,
                        {"account_number": acct_num},
                    )
                    account_orders = _orders_from_payload(parse_tool_payload(orders_raw))
                except Exception as exc:  # noqa: BLE001
                    LOGGER.warning("get_equity_orders failed for •••%s: %s", acct_num[-4:], exc)
                    warnings.append(
                        f"Order sync failed for •••{acct_num[-4:]} ({role}): {exc}"
                    )
            for order in account_orders:
                order["account_number"] = acct_num
                order["account_role"] = role
            all_orders.extend(account_orders)

            portfolios[acct_num] = portfolio_data
            account_summaries.append(
                {
                    "role": role,
                    "account_number": acct_num,
                    "nickname": account.get("nickname"),
                    "brokerage_account_type": account.get("brokerage_account_type"),
                    "agentic_allowed": account.get("agentic_allowed"),
                    "is_default": account.get("is_default"),
                    "equity_position_count": len(positions),
                    "option_position_count": len(options),
                    "position_count": len(positions) + len(options),
                    "order_count": len(account_orders),
                }
            )

        _enrich_market_values(client, all_positions, tool_names)
        all_positions = _trim_positions_for_sync(all_positions)

        all_orders.sort(
            key=lambda row: str(row.get("updated_at") or row.get("created_at") or ""),
            reverse=True,
        )
        seen_keys: set[str] = set()
        trimmed_orders: list[dict[str, Any]] = []
        for row in all_orders:
            key = f"{row.get('account_number', '')}\0{row.get('robinhood_order_id', '')}"
            if key in seen_keys:
                continue
            seen_keys.add(key)
            trimmed_orders.append(row)
            if len(trimmed_orders) >= ORDERS_SYNC_LIMIT:
                break
        all_orders = trimmed_orders

        agentic_account = agentic or {}
        return {
            "ok": True,
            "started_at": started,
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "agentic_account_number": str(agentic_account.get("account_number", "")) or None,
            "agentic_nickname": agentic_account.get("nickname"),
            "mcp_tool_count": len(tool_names),
            "option_positions_tool_available": option_tool_available,
            "orders_sync_limit": ORDERS_SYNC_LIMIT,
            "sync_all_accounts": sync_all,
            "warnings": warnings,
            "accounts": account_summaries,
            "portfolios": portfolios,
            "positions": all_positions,
            "orders": all_orders,
        }
    finally:
        try:
            client.close_session()
        except Exception:  # noqa: BLE001
            pass
