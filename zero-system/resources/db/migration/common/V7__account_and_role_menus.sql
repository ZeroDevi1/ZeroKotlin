ALTER TABLE sys_user ADD COLUMN email VARCHAR(254);
ALTER TABLE sys_user ADD COLUMN mobile VARCHAR(32);

ALTER TABLE sys_role ADD COLUMN direct_menus INTEGER NOT NULL DEFAULT 0 CHECK (direct_menus IN (0, 1));

CREATE TABLE sys_role_menu (
    role_id VARCHAR(36) NOT NULL,
    menu_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (role_id, menu_id),
    FOREIGN KEY (role_id) REFERENCES sys_role (id),
    FOREIGN KEY (menu_id) REFERENCES sys_menu (id)
);

CREATE INDEX idx_sys_role_menu_menu ON sys_role_menu (menu_id);
