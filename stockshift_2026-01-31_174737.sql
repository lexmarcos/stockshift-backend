--
-- PostgreSQL database dump
--

\restrict koOImNKwhNxKXWlw5shFNZuyw8UOaCf9udpWT5Snact42eap4tk3yHqInYuKaP6

-- Dumped from database version 16.10
-- Dumped by pg_dump version 16.11 (Ubuntu 16.11-0ubuntu0.24.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: batches; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.batches (
    expiration_date date,
    manufactured_date date,
    quantity numeric(15,3) NOT NULL,
    cost_price bigint,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    selling_price bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    version bigint NOT NULL,
    id uuid NOT NULL,
    origin_batch_id uuid,
    origin_transfer_id uuid,
    product_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    batch_code character varying(100) NOT NULL
);


ALTER TABLE public.batches OWNER TO postgres;

--
-- Name: brands; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.brands (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    logo_url character varying(500),
    name character varying(255) NOT NULL
);


ALTER TABLE public.brands OWNER TO postgres;

--
-- Name: categories; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.categories (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    parent_category_id uuid,
    tenant_id uuid NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    attributes_schema jsonb
);


ALTER TABLE public.categories OWNER TO postgres;

--
-- Name: inventory_ledger; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.inventory_ledger (
    balance_after numeric(15,3),
    quantity numeric(15,3) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    batch_id uuid,
    created_by uuid NOT NULL,
    id uuid NOT NULL,
    product_id uuid NOT NULL,
    reference_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    transfer_item_id uuid,
    warehouse_id uuid,
    entry_type character varying(50) NOT NULL,
    reference_type character varying(50) NOT NULL,
    notes text,
    CONSTRAINT inventory_ledger_entry_type_check CHECK (((entry_type)::text = ANY ((ARRAY['PURCHASE_IN'::character varying, 'SALE_OUT'::character varying, 'ADJUSTMENT_IN'::character varying, 'ADJUSTMENT_OUT'::character varying, 'TRANSFER_OUT'::character varying, 'TRANSFER_IN_TRANSIT'::character varying, 'TRANSFER_IN'::character varying, 'TRANSFER_TRANSIT_CONSUMED'::character varying, 'TRANSFER_LOSS'::character varying, 'RETURN_IN'::character varying])::text[])))
);


ALTER TABLE public.inventory_ledger OWNER TO postgres;

--
-- Name: permissions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.permissions (
    id uuid NOT NULL,
    action character varying(50) NOT NULL,
    resource character varying(50) NOT NULL,
    scope character varying(50) NOT NULL,
    description character varying(255),
    CONSTRAINT permissions_action_check CHECK (((action)::text = ANY ((ARRAY['CREATE'::character varying, 'READ'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying, 'APPROVE'::character varying, 'EXECUTE'::character varying, 'VALIDATE'::character varying, 'RESOLVE'::character varying, 'CANCEL'::character varying])::text[]))),
    CONSTRAINT permissions_resource_check CHECK (((resource)::text = ANY ((ARRAY['PRODUCT'::character varying, 'STOCK'::character varying, 'SALE'::character varying, 'USER'::character varying, 'REPORT'::character varying, 'WAREHOUSE'::character varying, 'TRANSFER'::character varying, 'SALES'::character varying])::text[]))),
    CONSTRAINT permissions_scope_check CHECK (((scope)::text = ANY ((ARRAY['ALL'::character varying, 'OWN_WAREHOUSE'::character varying, 'OWN'::character varying, 'TENANT'::character varying, 'OWNED'::character varying])::text[])))
);


ALTER TABLE public.permissions OWNER TO postgres;

--
-- Name: product_kits; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.product_kits (
    quantity integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    component_product_id uuid NOT NULL,
    id uuid NOT NULL,
    kit_product_id uuid NOT NULL
);


ALTER TABLE public.product_kits OWNER TO postgres;

--
-- Name: products; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.products (
    active boolean NOT NULL,
    has_expiration boolean NOT NULL,
    is_kit boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    brand_id uuid,
    category_id uuid,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    barcode_type character varying(20),
    barcode character varying(100),
    sku character varying(100),
    image_url character varying(500),
    description character varying(255),
    name character varying(255) NOT NULL,
    attributes jsonb,
    CONSTRAINT products_barcode_type_check CHECK (((barcode_type)::text = ANY ((ARRAY['EXTERNAL'::character varying, 'GENERATED'::character varying])::text[])))
);


ALTER TABLE public.products OWNER TO postgres;

--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.refresh_tokens (
    created_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token character varying(255) NOT NULL
);


ALTER TABLE public.refresh_tokens OWNER TO postgres;

--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.role_permissions (
    permission_id uuid NOT NULL,
    role_id uuid NOT NULL
);


ALTER TABLE public.role_permissions OWNER TO postgres;

--
-- Name: roles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.roles (
    is_system_role boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(255)
);


ALTER TABLE public.roles OWNER TO postgres;

--
-- Name: sale_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.sale_items (
    quantity numeric(15,3) NOT NULL,
    subtotal numeric(15,2) NOT NULL,
    unit_price numeric(15,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    batch_id uuid,
    id uuid NOT NULL,
    product_id uuid NOT NULL,
    sale_id uuid NOT NULL
);


ALTER TABLE public.sale_items OWNER TO postgres;

--
-- Name: sales; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.sales (
    discount numeric(15,2),
    subtotal numeric(15,2) NOT NULL,
    total numeric(15,2) NOT NULL,
    cancelled_at timestamp(6) without time zone,
    completed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    customer_id bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    cancelled_by uuid,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    warehouse_id uuid NOT NULL,
    payment_method character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    customer_name character varying(200),
    cancellation_reason text,
    notes text,
    CONSTRAINT sales_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['CASH'::character varying, 'DEBIT_CARD'::character varying, 'CREDIT_CARD'::character varying, 'INSTALLMENT'::character varying, 'PIX'::character varying, 'BANK_TRANSFER'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT sales_status_check CHECK (((status)::text = ANY ((ARRAY['COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.sales OWNER TO postgres;

--
-- Name: scan_logs; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.scan_logs (
    quantity numeric(15,3) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    processed_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    idempotency_key uuid NOT NULL,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    transfer_item_id uuid NOT NULL,
    barcode character varying(255) NOT NULL
);


ALTER TABLE public.scan_logs OWNER TO postgres;

--
-- Name: tenants; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.tenants (
    is_active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    document character varying(20),
    phone character varying(20),
    business_name character varying(255) NOT NULL,
    email character varying(255) NOT NULL
);


ALTER TABLE public.tenants OWNER TO postgres;

--
-- Name: transfer_discrepancy; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.transfer_discrepancy (
    difference numeric(15,3) NOT NULL,
    expected_quantity numeric(15,3) NOT NULL,
    received_quantity numeric(15,3) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    resolved_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    resolved_by uuid,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    transfer_item_id uuid NOT NULL,
    discrepancy_type character varying(20) NOT NULL,
    resolution character varying(30),
    status character varying(30) NOT NULL,
    resolution_notes text,
    CONSTRAINT transfer_discrepancy_discrepancy_type_check CHECK (((discrepancy_type)::text = ANY ((ARRAY['SHORTAGE'::character varying, 'EXCESS'::character varying])::text[]))),
    CONSTRAINT transfer_discrepancy_resolution_check CHECK (((resolution)::text = ANY ((ARRAY['WRITE_OFF'::character varying, 'FOUND'::character varying, 'RETURN_TRANSIT'::character varying, 'ACCEPTED'::character varying])::text[]))),
    CONSTRAINT transfer_discrepancy_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_RESOLUTION'::character varying, 'RESOLVED'::character varying, 'WRITTEN_OFF'::character varying])::text[])))
);


ALTER TABLE public.transfer_discrepancy OWNER TO postgres;

--
-- Name: transfer_events; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.transfer_events (
    created_at timestamp(6) without time zone NOT NULL,
    performed_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    performed_by uuid NOT NULL,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    event_type character varying(255) NOT NULL,
    from_status character varying(255),
    to_status character varying(255) NOT NULL,
    metadata jsonb,
    CONSTRAINT transfer_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['CREATED'::character varying, 'UPDATED'::character varying, 'DISPATCHED'::character varying, 'VALIDATION_STARTED'::character varying, 'ITEM_SCANNED'::character varying, 'COMPLETED'::character varying, 'COMPLETED_WITH_DISCREPANCY'::character varying, 'CANCELLED'::character varying, 'DISCREPANCY_RESOLVED'::character varying])::text[]))),
    CONSTRAINT transfer_events_from_status_check CHECK (((from_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_TRANSIT'::character varying, 'VALIDATION_IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'COMPLETED_WITH_DISCREPANCY'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT transfer_events_to_status_check CHECK (((to_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_TRANSIT'::character varying, 'VALIDATION_IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'COMPLETED_WITH_DISCREPANCY'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.transfer_events OWNER TO postgres;

--
-- Name: transfer_in_transit; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.transfer_in_transit (
    quantity numeric(15,3) NOT NULL,
    consumed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    product_id uuid NOT NULL,
    source_batch_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    transfer_item_id uuid NOT NULL
);


ALTER TABLE public.transfer_in_transit OWNER TO postgres;

--
-- Name: transfer_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.transfer_items (
    expected_quantity numeric(15,3) NOT NULL,
    received_quantity numeric(15,3),
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    destination_batch_id uuid,
    id uuid NOT NULL,
    product_id uuid NOT NULL,
    source_batch_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    transfer_id uuid NOT NULL,
    item_status character varying(30) NOT NULL,
    CONSTRAINT transfer_items_item_status_check CHECK (((item_status)::text = ANY ((ARRAY['PENDING'::character varying, 'RECEIVED'::character varying, 'PARTIAL'::character varying, 'EXCESS'::character varying, 'MISSING'::character varying])::text[])))
);


ALTER TABLE public.transfer_items OWNER TO postgres;

--
-- Name: transfers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.transfers (
    cancelled_at timestamp(6) without time zone,
    completed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    dispatched_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    validation_started_at timestamp(6) without time zone,
    version bigint NOT NULL,
    cancelled_by uuid,
    completed_by uuid,
    created_by uuid NOT NULL,
    destination_warehouse_id uuid NOT NULL,
    dispatched_by uuid,
    id uuid NOT NULL,
    source_warehouse_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    validation_started_by uuid,
    status character varying(50) NOT NULL,
    transfer_code character varying(50) NOT NULL,
    cancellation_reason text,
    notes text,
    CONSTRAINT transfers_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_TRANSIT'::character varying, 'VALIDATION_IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'COMPLETED_WITH_DISCREPANCY'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.transfers OWNER TO postgres;

--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_roles (
    role_id uuid NOT NULL,
    user_id uuid NOT NULL
);


ALTER TABLE public.user_roles OWNER TO postgres;

--
-- Name: user_warehouses; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_warehouses (
    user_id uuid NOT NULL,
    warehouse_id uuid NOT NULL
);


ALTER TABLE public.user_warehouses OWNER TO postgres;

--
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    is_active boolean NOT NULL,
    must_change_password boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    last_login timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    full_name character varying(255) NOT NULL,
    password character varying(255) NOT NULL
);


ALTER TABLE public.users OWNER TO postgres;

--
-- Name: warehouses; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.warehouses (
    is_active boolean NOT NULL,
    state character varying(2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    code character varying(20) NOT NULL,
    city character varying(100) NOT NULL,
    address text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.warehouses OWNER TO postgres;

--
-- Name: batches batches_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.batches
    ADD CONSTRAINT batches_pkey PRIMARY KEY (id);


--
-- Name: batches batches_tenant_id_batch_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.batches
    ADD CONSTRAINT batches_tenant_id_batch_code_key UNIQUE (tenant_id, batch_code);


--
-- Name: brands brands_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.brands
    ADD CONSTRAINT brands_pkey PRIMARY KEY (id);


--
-- Name: brands brands_tenant_id_name_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.brands
    ADD CONSTRAINT brands_tenant_id_name_key UNIQUE (tenant_id, name);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: categories categories_tenant_id_name_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_tenant_id_name_key UNIQUE (tenant_id, name);


--
-- Name: inventory_ledger inventory_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inventory_ledger
    ADD CONSTRAINT inventory_ledger_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_resource_action_scope_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_resource_action_scope_key UNIQUE (resource, action, scope);


--
-- Name: product_kits product_kits_kit_product_id_component_product_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.product_kits
    ADD CONSTRAINT product_kits_kit_product_id_component_product_id_key UNIQUE (kit_product_id, component_product_id);


--
-- Name: product_kits product_kits_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.product_kits
    ADD CONSTRAINT product_kits_pkey PRIMARY KEY (id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: products products_tenant_id_barcode_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_tenant_id_barcode_key UNIQUE (tenant_id, barcode);


--
-- Name: products products_tenant_id_sku_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_tenant_id_sku_key UNIQUE (tenant_id, sku);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_key UNIQUE (token);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (permission_id, role_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: roles roles_tenant_id_name_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_tenant_id_name_key UNIQUE (tenant_id, name);


--
-- Name: sale_items sale_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sale_items
    ADD CONSTRAINT sale_items_pkey PRIMARY KEY (id);


--
-- Name: sales sales_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_pkey PRIMARY KEY (id);


--
-- Name: scan_logs scan_logs_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.scan_logs
    ADD CONSTRAINT scan_logs_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: scan_logs scan_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.scan_logs
    ADD CONSTRAINT scan_logs_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_document_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_document_key UNIQUE (document);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: transfer_discrepancy transfer_discrepancy_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_discrepancy
    ADD CONSTRAINT transfer_discrepancy_pkey PRIMARY KEY (id);


--
-- Name: transfer_events transfer_events_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_events
    ADD CONSTRAINT transfer_events_pkey PRIMARY KEY (id);


--
-- Name: transfer_in_transit transfer_in_transit_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_in_transit
    ADD CONSTRAINT transfer_in_transit_pkey PRIMARY KEY (id);


--
-- Name: transfer_items transfer_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_items
    ADD CONSTRAINT transfer_items_pkey PRIMARY KEY (id);


--
-- Name: transfers transfers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_pkey PRIMARY KEY (id);


--
-- Name: transfers transfers_tenant_id_transfer_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT transfers_tenant_id_transfer_code_key UNIQUE (tenant_id, transfer_code);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (role_id, user_id);


--
-- Name: user_warehouses user_warehouses_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_warehouses
    ADD CONSTRAINT user_warehouses_pkey PRIMARY KEY (user_id, warehouse_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_tenant_id_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_tenant_id_email_key UNIQUE (tenant_id, email);


--
-- Name: warehouses warehouses_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_pkey PRIMARY KEY (id);


--
-- Name: warehouses warehouses_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: warehouses warehouses_tenant_id_name_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.warehouses
    ADD CONSTRAINT warehouses_tenant_id_name_key UNIQUE (tenant_id, name);


--
-- Name: refresh_tokens fk1lih5y2npsf8u5o3vhdb9y0os; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk1lih5y2npsf8u5o3vhdb9y0os FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: transfer_discrepancy fk1rcddh9hp5qe2y7plhudhlgpk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_discrepancy
    ADD CONSTRAINT fk1rcddh9hp5qe2y7plhudhlgpk FOREIGN KEY (resolved_by) REFERENCES public.users(id);


--
-- Name: transfer_in_transit fk31cc662k4g62yc2qbrro67014; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_in_transit
    ADD CONSTRAINT fk31cc662k4g62yc2qbrro67014 FOREIGN KEY (source_batch_id) REFERENCES public.batches(id);


--
-- Name: transfers fk394fqgdi4cn9tlt35g1tuoy0o; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT fk394fqgdi4cn9tlt35g1tuoy0o FOREIGN KEY (source_warehouse_id) REFERENCES public.warehouses(id);


--
-- Name: sales fk5bgaw8g0rrbqdvafq36g58smk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT fk5bgaw8g0rrbqdvafq36g58smk FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: product_kits fk5r3nwcbt56wwr8wm83ftpffgu; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.product_kits
    ADD CONSTRAINT fk5r3nwcbt56wwr8wm83ftpffgu FOREIGN KEY (kit_product_id) REFERENCES public.products(id);


--
-- Name: transfers fk72maf1ivw09l7tb60v664tvqc; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT fk72maf1ivw09l7tb60v664tvqc FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: sale_items fk7tcpbc5c5mpnm8fl2phl8ep7l; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sale_items
    ADD CONSTRAINT fk7tcpbc5c5mpnm8fl2phl8ep7l FOREIGN KEY (sale_id) REFERENCES public.sales(id);


--
-- Name: sales fk844yv2fo4hrjxme8b414ip85r; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT fk844yv2fo4hrjxme8b414ip85r FOREIGN KEY (cancelled_by) REFERENCES public.users(id);


--
-- Name: sale_items fk8g0sjiqs7tg055o06p6wawu39; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sale_items
    ADD CONSTRAINT fk8g0sjiqs7tg055o06p6wawu39 FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: categories fk9il7y6fehxwunjeepq0n7g5rd; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT fk9il7y6fehxwunjeepq0n7g5rd FOREIGN KEY (parent_category_id) REFERENCES public.categories(id);


--
-- Name: products fka3a4mpsfdf4d2y6r8ra3sc8mv; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fka3a4mpsfdf4d2y6r8ra3sc8mv FOREIGN KEY (brand_id) REFERENCES public.brands(id);


--
-- Name: transfer_items fkawx4ken1qng8oosupgfnj5yhe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_items
    ADD CONSTRAINT fkawx4ken1qng8oosupgfnj5yhe FOREIGN KEY (source_batch_id) REFERENCES public.batches(id);


--
-- Name: transfer_items fkb9yie1ivcsqntq9x1al2v4inx; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_items
    ADD CONSTRAINT fkb9yie1ivcsqntq9x1al2v4inx FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: role_permissions fkegdk29eiy7mdtefy5c7eirr6e; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkegdk29eiy7mdtefy5c7eirr6e FOREIGN KEY (permission_id) REFERENCES public.permissions(id);


--
-- Name: product_kits fkfcd7soacv9l8rqlkf2590hamt; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.product_kits
    ADD CONSTRAINT fkfcd7soacv9l8rqlkf2590hamt FOREIGN KEY (component_product_id) REFERENCES public.products(id);


--
-- Name: transfer_items fkfnfm7pjoj2a5oawwtbcp3c3j0; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_items
    ADD CONSTRAINT fkfnfm7pjoj2a5oawwtbcp3c3j0 FOREIGN KEY (destination_batch_id) REFERENCES public.batches(id);


--
-- Name: transfer_in_transit fkgcv4mmf60tsj49ijueispo2ky; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_in_transit
    ADD CONSTRAINT fkgcv4mmf60tsj49ijueispo2ky FOREIGN KEY (transfer_id) REFERENCES public.transfers(id);


--
-- Name: transfers fkgh67lc4dt5fakfdiu9slx1tvm; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT fkgh67lc4dt5fakfdiu9slx1tvm FOREIGN KEY (destination_warehouse_id) REFERENCES public.warehouses(id);


--
-- Name: user_roles fkh8ciramu9cc9q3qcqiv4ue8a6; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: sales fkhf9hp5u4um5na1qrld83f70l2; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT fkhf9hp5u4um5na1qrld83f70l2 FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id);


--
-- Name: user_roles fkhfh9dx7w3ubf1co1vdev94g3f; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: transfers fkiob8epw5x0m389q5uv7ok4g5f; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT fkiob8epw5x0m389q5uv7ok4g5f FOREIGN KEY (completed_by) REFERENCES public.users(id);


--
-- Name: batches fkion5u664pj3exwgiskge8h1vc; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.batches
    ADD CONSTRAINT fkion5u664pj3exwgiskge8h1vc FOREIGN KEY (origin_batch_id) REFERENCES public.batches(id);


--
-- Name: user_warehouses fkioqdhamfmvp9nhpeq7l8m75jk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_warehouses
    ADD CONSTRAINT fkioqdhamfmvp9nhpeq7l8m75jk FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: batches fkjb38v1mk479a6t6ay2mewo03m; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.batches
    ADD CONSTRAINT fkjb38v1mk479a6t6ay2mewo03m FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: transfers fkjbgh7c2ehxfmbawxlicii8721; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT fkjbgh7c2ehxfmbawxlicii8721 FOREIGN KEY (dispatched_by) REFERENCES public.users(id);


--
-- Name: transfers fkjr2967bolfyiwy8ogi2slas3t; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT fkjr2967bolfyiwy8ogi2slas3t FOREIGN KEY (validation_started_by) REFERENCES public.users(id);


--
-- Name: transfers fklgdn6kaysv079tdvgu8osh2t6; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfers
    ADD CONSTRAINT fklgdn6kaysv079tdvgu8osh2t6 FOREIGN KEY (cancelled_by) REFERENCES public.users(id);


--
-- Name: transfer_in_transit fkmx6uhod43ywit862h2e38qjkp; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_in_transit
    ADD CONSTRAINT fkmx6uhod43ywit862h2e38qjkp FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: role_permissions fkn5fotdgk8d1xvo8nav9uv3muc; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: sale_items fknb1w2p9juo322nxinlavx14od; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sale_items
    ADD CONSTRAINT fknb1w2p9juo322nxinlavx14od FOREIGN KEY (batch_id) REFERENCES public.batches(id);


--
-- Name: products fkog2rp4qthbtt2lfyhfo32lsw9; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fkog2rp4qthbtt2lfyhfo32lsw9 FOREIGN KEY (category_id) REFERENCES public.categories(id);


--
-- Name: transfer_items fkoptxlfe17g3eraxt6ja0b55gn; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_items
    ADD CONSTRAINT fkoptxlfe17g3eraxt6ja0b55gn FOREIGN KEY (transfer_id) REFERENCES public.transfers(id);


--
-- Name: transfer_in_transit fkpfut5awrqqsqmn4l5iu29mf5n; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_in_transit
    ADD CONSTRAINT fkpfut5awrqqsqmn4l5iu29mf5n FOREIGN KEY (transfer_item_id) REFERENCES public.transfer_items(id);


--
-- Name: user_warehouses fkpj98sx7hdsyc99elrknoh0pwh; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_warehouses
    ADD CONSTRAINT fkpj98sx7hdsyc99elrknoh0pwh FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id);


--
-- Name: batches fkqeey78x7wm8mwonxbfk5ka1fm; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.batches
    ADD CONSTRAINT fkqeey78x7wm8mwonxbfk5ka1fm FOREIGN KEY (origin_transfer_id) REFERENCES public.transfers(id);


--
-- Name: transfer_discrepancy fkrlpmes8ceax14ksth5dae20fm; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_discrepancy
    ADD CONSTRAINT fkrlpmes8ceax14ksth5dae20fm FOREIGN KEY (transfer_item_id) REFERENCES public.transfer_items(id);


--
-- Name: batches fksgp85bi3ebbvbk0agae373ltl; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.batches
    ADD CONSTRAINT fksgp85bi3ebbvbk0agae373ltl FOREIGN KEY (warehouse_id) REFERENCES public.warehouses(id);


--
-- Name: transfer_discrepancy fkt62ycy4n4ttsmo0oytj63dyjm; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.transfer_discrepancy
    ADD CONSTRAINT fkt62ycy4n4ttsmo0oytj63dyjm FOREIGN KEY (transfer_id) REFERENCES public.transfers(id);


--
-- PostgreSQL database dump complete
--

\unrestrict koOImNKwhNxKXWlw5shFNZuyw8UOaCf9udpWT5Snact42eap4tk3yHqInYuKaP6

