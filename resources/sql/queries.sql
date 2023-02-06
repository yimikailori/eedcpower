-- :name get-customer-info :? :1
-- :doc get customer info
SELECT * FROM func_get_customer_info(:sub::bigint)



-- :name new-session :? :1
-- :doc Creates a new session entry in the database.
select * from func_session_insert(:session-id, :subscriber::bigint, :session-data::json, :max-age::text, :allow-resume?)

-- :name get-session-data :? :1
-- :doc Retrieve session data from the database.
select msisdn as subscriber_no, session_data
from tbl_ussd_session
where session_id = :session-id

-- :name update-session-data :? :1
-- :doc Update session data.
select * from func_session_update(:session-id, :subscriber::bigint, :session-data::json)

-- :name close-session :? :1
-- :doc Terminate a session.
select * from func_session_terminate(:session-id, :subscriber::bigint)

-- :name clear-expired-sessions! :! :n
delete from tbl_ussd_session
where time_end < now();



-- :name get-loan-max-bals :? :*
-- :doc get loan maximums
select loan_type::text, loanable_amount, denom_max_bal,advance_name
from tbl_denoms
where denom_max_bal >= 0
order by loan_type, loanable_amount
