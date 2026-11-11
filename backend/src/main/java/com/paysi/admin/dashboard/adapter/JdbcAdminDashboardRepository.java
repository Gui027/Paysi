package com.paysi.admin.dashboard.adapter;

import com.paysi.admin.dashboard.domain.AdminDashboardView;
import com.paysi.admin.dashboard.port.AdminDashboardRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAdminDashboardRepository implements AdminDashboardRepository {
    private final JdbcTemplate jdbc;

    public JdbcAdminDashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveSession(String tokenHash, String username, Instant now, Instant expiresAt) {
        jdbc.update("""
                insert into admin_dashboard_sessions(token_hash,username,last_seen_at,expires_at)
                values (?,?,?,?)
                on conflict (token_hash) do update set username=excluded.username,
                  last_seen_at=excluded.last_seen_at,expires_at=excluded.expires_at
                """, tokenHash, username, Timestamp.from(now), Timestamp.from(expiresAt));
    }

    @Override
    public Optional<StoredSession> findSession(String tokenHash) {
        return jdbc.query("select username,expires_at from admin_dashboard_sessions where token_hash=?",
                (rs, row) -> new StoredSession(rs.getString(1), rs.getTimestamp(2).toInstant()), tokenHash)
                .stream().findFirst();
    }

    @Override
    public void renewSession(String tokenHash, Instant now, Instant expiresAt) {
        jdbc.update("update admin_dashboard_sessions set last_seen_at=?,expires_at=? where token_hash=?",
                Timestamp.from(now), Timestamp.from(expiresAt), tokenHash);
    }

    @Override public void deleteSession(String tokenHash) {
        jdbc.update("delete from admin_dashboard_sessions where token_hash=?", tokenHash);
    }

    @Override public void deleteExpiredSessions(Instant now) {
        jdbc.update("delete from admin_dashboard_sessions where expires_at<=?", Timestamp.from(now));
    }

    @Override
    public AdminDashboardView.Summary summary(Instant periodStart) {
        return jdbc.queryForObject("""
                select
                  coalesce((select sum(greatest(c.amount_cents-c.refunded_cents,0))
                              from charges c where c.confirmed_at is not null),0),
                  coalesce((select sum(greatest(c.amount_cents-c.refunded_cents,0))
                              from charges c where c.confirmed_at>=?),0),
                  (select count(*) from charges c where c.confirmed_at>=?),
                  (select count(*) from subscriptions s where s.status in ('TRIAL','ACTIVE')),
                  (select count(*) from accounts a where a.created_at>=?)
                """, (rs, row) -> new AdminDashboardView.Summary(rs.getLong(1), rs.getLong(2),
                        rs.getLong(3), rs.getLong(4), rs.getLong(5)), Timestamp.from(periodStart),
                Timestamp.from(periodStart), Timestamp.from(periodStart));
    }

    @Override
    public List<AdminDashboardView.RevenuePoint> revenueTimeline(LocalDate firstDay, LocalDate lastDay) {
        return jdbc.query("""
                with days as (
                  select generate_series(?::date,?::date,interval '1 day')::date as day
                ), revenue as (
                  select (c.confirmed_at at time zone 'UTC')::date as day,
                         sum(greatest(c.amount_cents-c.refunded_cents,0)) as amount,
                         count(*) as sales
                    from charges c
                   where c.confirmed_at>=?::date and c.confirmed_at<?::date + interval '1 day'
                   group by 1
                )
                select d.day,coalesce(r.amount,0),coalesce(r.sales,0)
                  from days d left join revenue r on r.day=d.day order by d.day
                """, (rs, row) -> new AdminDashboardView.RevenuePoint(rs.getDate(1).toLocalDate(),
                        rs.getLong(2), rs.getLong(3)), firstDay, lastDay, firstDay, lastDay);
    }

    @Override
    public List<AdminDashboardView.AccountRow> accounts(String query, int limit, int offset) {
        String pattern = "%" + query + "%";
        return jdbc.query("""
                select a.id,a.full_name,a.email::text,a.status,a.created_at,
                       coalesce(sum(greatest(c.amount_cents-c.refunded_cents,0))
                         filter (where c.confirmed_at is not null),0) revenue,
                       count(c.id) filter (where c.confirmed_at is not null) paid_sales
                  from accounts a
                  left join products p on p.seller_id=a.id
                  left join offers f on f.product_id=p.id
                  left join orders o on o.offer_id=f.id
                  left join charges c on c.order_id=o.id
                 where (?='' or a.full_name ilike ? or a.email::text ilike ? or a.id::text ilike ?)
                 group by a.id,a.full_name,a.email,a.status,a.created_at
                 order by a.created_at desc,a.id desc limit ? offset ?
                """, (rs, row) -> new AdminDashboardView.AccountRow(rs.getObject(1, java.util.UUID.class),
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(6), rs.getLong(7),
                        rs.getTimestamp(5).toInstant()), query, pattern, pattern, pattern, limit, offset);
    }

    @Override
    public long countAccounts(String query) {
        String pattern = "%" + query + "%";
        Long count = jdbc.queryForObject("""
                select count(*) from accounts a
                 where (?='' or a.full_name ilike ? or a.email::text ilike ? or a.id::text ilike ?)
                """, Long.class, query, pattern, pattern, pattern);
        return count == null ? 0 : count;
    }
}
