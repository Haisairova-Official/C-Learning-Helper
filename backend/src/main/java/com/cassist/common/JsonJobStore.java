package com.cassist.common;

import com.cassist.identity.Actor;
import com.cassist.identity.Role;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Private module records. Table names are a closed server-side allowlist. */
@Component
public class JsonJobStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public JsonJobStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    private String table(String table) {
        if (!Set.of("import_job", "ai_analysis").contains(table)) throw new IllegalArgumentException("Unknown job table");
        return table;
    }
    public void create(String table, String id, Actor actor, String status, Object payload) {
        jdbc.update("INSERT INTO "+table(table)+" (id,owner_id,status,payload_json,created_at,updated_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
            id,actor.id(),status,mapper.writeValueAsString(payload));
    }
    public void save(String table,String id,String status,Object payload) {
        jdbc.update("UPDATE "+table(table)+" SET status=?,payload_json=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",status,mapper.writeValueAsString(payload),id);
    }
    public <T> T read(String table,String id,Actor actor,Class<T> type) {
        List<Row> rows=jdbc.query("SELECT owner_id,payload_json FROM "+table(table)+" WHERE id=?",(rs,n)->new Row(rs.getString(1),rs.getString(2)),id);
        if(rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,"NOT_FOUND","记录不存在");
        Row row=rows.getFirst();
        if(!row.ownerId.equals(actor.id()) && actor.role()!=Role.ADMIN)
            throw new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","无权访问该记录");
        return mapper.readValue(row.json,type);
    }
    private record Row(String ownerId,String json) {}
}
