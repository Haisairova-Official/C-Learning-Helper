CREATE TABLE problem_library (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(300) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_library_owner FOREIGN KEY(owner_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE library_problem (
    library_id VARCHAR(36) NOT NULL,
    problem_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    PRIMARY KEY(library_id,problem_id),
    CONSTRAINT fk_library_problem_library FOREIGN KEY(library_id) REFERENCES problem_library(id),
    CONSTRAINT fk_library_problem_problem FOREIGN KEY(problem_id) REFERENCES problem(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Imported problem sets become libraries; assignment snapshots remain separate.
INSERT INTO problem_library(id,owner_id,title,created_at,updated_at)
SELECT ps.id,ps.owner_id,LEFT(ps.title,300),ps.created_at,ps.created_at FROM problem_set ps
WHERE NOT EXISTS(SELECT 1 FROM assignment a WHERE a.problem_set_id=ps.id);
INSERT INTO library_problem(library_id,problem_id,position)
SELECT psi.problem_set_id,pv.problem_id,MIN(psi.position) FROM problem_set_item psi
JOIN problem_library l ON l.id=psi.problem_set_id JOIN problem_version pv ON pv.id=psi.problem_version_id
GROUP BY psi.problem_set_id,pv.problem_id;
-- Keep every existing question reachable, including those only used in assignments.
INSERT INTO problem_library(id,owner_id,title,created_at,updated_at)
SELECT REPLACE(p.owner_id,'-',''),p.owner_id,'未分类题目',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
FROM problem p GROUP BY p.owner_id;
INSERT INTO library_problem(library_id,problem_id,position)
SELECT REPLACE(p.owner_id,'-',''),p.id,1 FROM problem p
WHERE NOT EXISTS(SELECT 1 FROM library_problem lp WHERE lp.problem_id=p.id);
