-- Student numbers are independent school data; never derive them from login names.
ALTER TABLE app_user ADD COLUMN student_number VARCHAR(32) NULL;
