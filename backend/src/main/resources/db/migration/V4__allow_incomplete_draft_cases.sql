-- Incomplete imported drafts preserve their fields; publication validates completeness.
ALTER TABLE test_case MODIFY input_text MEDIUMTEXT NULL;
ALTER TABLE test_case MODIFY expected_output MEDIUMTEXT NULL;
ALTER TABLE test_case MODIFY score DECIMAL(10,2) NULL;
