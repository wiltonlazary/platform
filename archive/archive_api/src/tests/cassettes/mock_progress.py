#!/usr/bin/env python
# -*- encoding: utf-8
"""
This is a dummy version of the progress app, which was used to create
the responses for the ProgressManager tests.

It mimics the external interface but not the functionlity!
"""

from flask import Flask, request, Response
import uuid

app = Flask(__name__)


@app.route('/progress', methods=['POST'])
def post_route():
    # e.g. http://localhost:6000?status=500
    if '?status=' in request.form['uploadUrl']:
        return b'', int(request.form['uploadUrl'].split('=')[1])

    # e.g. http://localhost:6000?location=no
    if request.form['uploadUrl'].endswith('?location=no'):
        return b'', 202

    resp = Response()
    resp.headers['Location'] = f'/progress/{str(uuid.uuid4())}'
    return resp, 202


@app.route('/progress/<id>')
def get_route(id):
    return 'bar'


app.run(debug=True, port=6000)
