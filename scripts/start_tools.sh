logs_dir="$(cd "$(dirname "$0")/../logs/"; pwd)"
echo "Log files directory set to $logs_dir."

echo "Starting Alpino server..."
PROLOGMAXSIZE=800M Alpino -notk -veryfast user_max=20000\
	server_kind=parse\
	server_port=42424\
	assume_input_is_tokenized=off\
	debug=1\
	-init_dict_p\
	batch_command=alpino_server > "$logs_dir/alpino.log" 2>&1 &

echo "Starting Solr server..."
cd $SOLR_HOME
nohup bin/solr start -s example/nlwiki -m 4g > "$logs_dir/solr.log" 2>&1 &

echo "Starting label lookup service..."
cd $LABEL_LOOKUP_HOME
nohup ./lookup-service.py sorted_list.dat > "$logs_dir/label-lookup.log" 2>&1 &

echo "Starting Fuseki server..."
cd $FUSEKI_HOME
nohup ./fuseki-server --port 3031 --loc $JENA_HOME/bin/db /dbpedia > "$logs_dir/fuseki.log" 2>&1 &
